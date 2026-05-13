package com.zyf.deepseek.dto;

public class ClientChatResponse {

    private String content;
    private String reasoning;
    private String model;

    public ClientChatResponse() {
    }

    public ClientChatResponse(String content, String model) {
        this(content, "", model);
    }

    public ClientChatResponse(String content, String reasoning, String model) {
        this.content = content;
        this.reasoning = reasoning;
        this.model = model;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
