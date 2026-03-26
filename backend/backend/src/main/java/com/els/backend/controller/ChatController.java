package com.els.backend.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "${app.frontend-origin}")
public class ChatController {

    private final ChatClient chatClient;
    private final SyncMcpToolCallbackProvider toolCallbackProvider;

    public ChatController(ChatClient.Builder chatClientBuilder,
                          SyncMcpToolCallbackProvider toolCallbackProvider) {
        this.chatClient = chatClientBuilder.build();
        this.toolCallbackProvider = toolCallbackProvider;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()
                || request.uid() == null || request.uid().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String systemPrompt = """
                You are a helpful financial assistant.
                If you call any tool, always include the user's uid argument exactly as provided:
                uid=%s
                """.formatted(request.uid().trim());

        String reply = chatClient.prompt()
                .system(systemPrompt)
                .user(request.message().trim())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .content();

        return ResponseEntity.ok(new ChatResponse(reply));
    }

    public record ChatRequest(String message, String uid) {
    }

    public record ChatResponse(String reply) {
    }
}
