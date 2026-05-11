package com.zyf.deepseek.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ClientChatRequest {

    /**
     * flash → deepseek-v4-flash（快速模式），pro → deepseek-v4-pro（专家模式）
     */
    @NotNull
    private ChatMode mode = ChatMode.flash;

    @NotEmpty
    @Valid
    private List<ChatMessageDto> messages;

    /** 对应网页「深度思考」开关，开启时请求携带 thinking 参数（见 DeepSeek V4 文档） */
    private boolean deepThinking;

    public ChatMode getMode() {
        return mode;
    }

    public void setMode(ChatMode mode) {
        this.mode = mode;
    }

    public List<ChatMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessageDto> messages) {
        this.messages = messages;
    }

    public boolean isDeepThinking() {
        return deepThinking;
    }

    public void setDeepThinking(boolean deepThinking) {
        this.deepThinking = deepThinking;
    }

    public enum ChatMode {
        flash,
        pro
    }
}
