package com.zyf.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;

public class ChatMessageDto {

    @NotBlank
    private String role;

    @NotBlank
    private String content;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @JsonIgnore
    public boolean isValidRole() {
        return "system".equals(role) || "user".equals(role) || "assistant".equals(role);
    }
}
