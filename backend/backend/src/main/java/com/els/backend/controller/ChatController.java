package com.els.backend.controller;

import org.springframework.web.bind.annotation.*;
import com.els.backend.service.ChatService;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:4200")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> body) {

        String message = body.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Map.of("reply", "Please enter a valid message.");
        }

        String reply = chatService.processMessage(message);

        return Map.of("reply", reply);
    }
}