package com.zyf.deepseek.dto;

public class ClientChatResponse {

    private String content;
    private String model;

    public ClientChatResponse() {
    }

    public ClientChatResponse(String content, String model) {
        this.content = content;
        this.model = model;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
