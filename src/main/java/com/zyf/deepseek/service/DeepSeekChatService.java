package com.zyf.deepseek.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zyf.deepseek.config.DeepSeekProperties;
import com.zyf.deepseek.dto.ChatMessageDto;
import com.zyf.deepseek.dto.ClientChatRequest;
import com.zyf.deepseek.dto.ClientChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Service
public class DeepSeekChatService {

    private static final String CHAT_PATH = "/chat/completions";
    private static final int MAX_MESSAGES = 40;
    private static final int MAX_CONTENT_CHARS = 24_000;

    private final WebClient deepSeekWebClient;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekChatService(WebClient deepSeekWebClient, DeepSeekProperties properties, ObjectMapper objectMapper) {
        this.deepSeekWebClient = deepSeekWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ClientChatResponse chat(ClientChatRequest request) {
        validateRequest(request);
        if (properties.getCloud() != null && properties.getCloud().isConfigured()) {
            return chatViaCloud(request);
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("未配置模型服务密钥；请在云端环境变量中设置 DEEPSEEK_API_KEY，或配置 CLOUD_CHAT_API_URL");
        }

        String model = request.getMode() == ClientChatRequest.ChatMode.pro
                ? properties.getModels().getPro()
                : properties.getModels().getFlash();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);

        ObjectNode thinking = objectMapper.createObjectNode();
        thinking.put("type", request.isDeepThinking() ? "enabled" : "disabled");
        body.set("thinking", thinking);
        if (request.isDeepThinking()) {
            body.put("reasoning_effort", "high");
        }

        ArrayNode messages = body.putArray("messages");
        for (ChatMessageDto m : request.getMessages()) {
            ObjectNode row = messages.addObject();
            row.put("role", m.getRole());
            row.put("content", m.getContent().trim());
        }

        try {
            JsonNode root = deepSeekWebClient.post()
                    .uri(CHAT_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().trim())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(Math.max(10, properties.getRequestTimeoutSeconds())))
                    .block();

            if (root == null || !root.has("choices") || root.get("choices").isEmpty()) {
                throw new IllegalStateException("模型服务返回格式异常");
            }
            JsonNode message = root.get("choices").get(0).get("message");
            String content = extractText(message, "content");
            String reasoning = extractText(message, "reasoning_content");
            if (content.isEmpty() && !reasoning.isEmpty()) {
                content = reasoning;
                reasoning = "";
            }
            String usedModel = root.has("model") ? root.get("model").asText(model) : model;
            return new ClientChatResponse(content, reasoning, usedModel);
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "模型服务请求失败：" + e.getStatusCode() + " " + sanitizeError(e.getResponseBodyAsString()),
                    e
            );
        } catch (RuntimeException e) {
            if (isTimeout(e)) {
                throw new IllegalStateException("模型服务请求超时，请稍后重试", e);
            }
            throw e;
        }
    }

    private ClientChatResponse chatViaCloud(ClientChatRequest request) {
        try {
            WebClient.RequestBodySpec spec = deepSeekWebClient.post()
                    .uri(properties.getCloud().getChatUrl().trim());
            if (properties.getCloud().getToken() != null && !properties.getCloud().getToken().isBlank()) {
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getCloud().getToken().trim());
            }
            JsonNode root = spec.bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(Math.max(10, properties.getRequestTimeoutSeconds())))
                    .block();
            return parseCloudResponse(root);
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "云端模型代理请求失败：" + e.getStatusCode() + " " + sanitizeError(e.getResponseBodyAsString()),
                    e
            );
        } catch (RuntimeException e) {
            if (isTimeout(e)) {
                throw new IllegalStateException("云端模型代理请求超时，请稍后重试", e);
            }
            throw e;
        }
    }

    private static ClientChatResponse parseCloudResponse(JsonNode root) {
        if (root == null) {
            throw new IllegalStateException("云端模型代理返回为空");
        }
        String content = extractText(root, "content");
        String reasoning = extractText(root, "reasoning");
        String model = extractText(root, "model");

        if (content.isEmpty()) {
            content = extractText(root, "text");
        }
        if (content.isEmpty()) {
            content = extractText(root, "message");
        }
        JsonNode data = root.get("data");
        if (content.isEmpty() && data != null) {
            content = extractText(data, "content");
            reasoning = reasoning.isEmpty() ? extractText(data, "reasoning") : reasoning;
            model = model.isEmpty() ? extractText(data, "model") : model;
        }
        if (content.isEmpty() && root.has("choices") && root.get("choices").isArray() && !root.get("choices").isEmpty()) {
            JsonNode message = root.get("choices").get(0).get("message");
            content = extractText(message, "content");
            reasoning = extractText(message, "reasoning_content");
        }
        if (content.isEmpty()) {
            throw new IllegalStateException("云端模型代理返回格式异常");
        }
        return new ClientChatResponse(content, reasoning, model.isEmpty() ? "cloud-proxy" : model);
    }

    private static void validateRequest(ClientChatRequest request) {
        List<ChatMessageDto> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
        if (messages.size() > MAX_MESSAGES) {
            throw new IllegalArgumentException("单次请求最多保留 " + MAX_MESSAGES + " 条上下文消息");
        }
        for (ChatMessageDto m : messages) {
            if (m == null || !m.isValidRole()) {
                throw new IllegalArgumentException("非法 role: " + (m == null ? "null" : m.getRole()));
            }
            if (m.getContent() == null || m.getContent().isBlank()) {
                throw new IllegalArgumentException("消息内容不能为空");
            }
            if (m.getContent().length() > MAX_CONTENT_CHARS) {
                throw new IllegalArgumentException("单条消息过长，请缩短后再发送");
            }
        }
    }

    private static String extractText(JsonNode message, String field) {
        if (message == null || !message.has(field) || message.get(field).isNull()) {
            return "";
        }
        return message.get(field).asText("").trim();
    }

    private static String sanitizeError(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        String compact = detail.replaceAll("\\s+", " ").trim();
        if (compact.length() > 600) {
            return compact.substring(0, 600) + "...";
        }
        return compact;
    }

    private static boolean isTimeout(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof TimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
