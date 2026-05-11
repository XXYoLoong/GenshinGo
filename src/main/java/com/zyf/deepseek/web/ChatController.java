package com.zyf.deepseek.web;

import com.zyf.deepseek.dto.ClientChatRequest;
import com.zyf.deepseek.dto.ClientChatResponse;
import com.zyf.deepseek.service.DeepSeekChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final DeepSeekChatService chatService;

    public ChatController(DeepSeekChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ClientChatResponse chat(@Valid @RequestBody ClientChatRequest request) {
        return chatService.chat(request);
    }
}
