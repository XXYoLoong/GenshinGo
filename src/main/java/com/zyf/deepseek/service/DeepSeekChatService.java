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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Service
public class DeepSeekChatService {

    private static final String CHAT_PATH = "/chat/completions";

    private final WebClient deepSeekWebClient;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekChatService(WebClient deepSeekWebClient, DeepSeekProperties properties, ObjectMapper objectMapper) {
        this.deepSeekWebClient = deepSeekWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ClientChatResponse chat(ClientChatRequest request) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("未配置 DEEPSEEK_API_KEY 环境变量");
        }
        for (ChatMessageDto m : request.getMessages()) {
            if (!m.isValidRole()) {
                throw new IllegalArgumentException("非法 role: " + m.getRole());
            }
        }

        String model = request.getMode() == ClientChatRequest.ChatMode.pro
                ? properties.getModels().getPro()
                : properties.getModels().getFlash();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        for (ChatMessageDto m : request.getMessages()) {
            ObjectNode row = messages.addObject();
            row.put("role", m.getRole());
            row.put("content", m.getContent());
        }
        if (request.isDeepThinking()) {
            ObjectNode thinking = objectMapper.createObjectNode();
            thinking.put("type", "enabled");
            body.set("thinking", thinking);
            body.put("reasoning_effort", "high");
        }

        try {
            JsonNode root = deepSeekWebClient.post()
                    .uri(CHAT_PATH)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (root == null || !root.has("choices") || root.get("choices").isEmpty()) {
                throw new IllegalStateException("DeepSeek 返回格式异常");
            }
            JsonNode message = root.get("choices").get(0).get("message");
            String content = extractAssistantText(message);
            String usedModel = root.has("model") ? root.get("model").asText(model) : model;
            return new ClientChatResponse(content, usedModel);
        } catch (WebClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            throw new IllegalStateException("DeepSeek 请求失败: " + e.getStatusCode() + " " + detail, e);
        }
    }

    private static String extractAssistantText(JsonNode message) {
        if (message == null) {
            return "";
        }
        String reasoning = "";
        if (message.has("reasoning_content") && !message.get("reasoning_content").isNull()) {
            reasoning = message.get("reasoning_content").asText("").trim();
        }
        String answer = "";
        if (message.has("content") && !message.get("content").isNull()) {
            answer = message.get("content").asText("").trim();
        }
        if (!reasoning.isEmpty() && !answer.isEmpty()) {
            return "【思考过程】\n" + reasoning + "\n\n【回答】\n" + answer;
        }
        if (!answer.isEmpty()) {
            return answer;
        }
        return reasoning;
    }
}
