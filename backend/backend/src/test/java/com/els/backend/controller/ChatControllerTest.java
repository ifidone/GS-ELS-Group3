package com.els.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private MockMvc mockMvc;
    private ChatClient.Builder chatClientBuilder;
    private SyncMcpToolCallbackProvider toolCallbackProvider;

    @BeforeEach
    void setUp() {
        chatClientBuilder = mock(ChatClient.Builder.class);
        toolCallbackProvider = mock(SyncMcpToolCallbackProvider.class);
        ChatController controller = new ChatController(chatClientBuilder, toolCallbackProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addPlaceholderValue("app.frontend-origin", "http://localhost")
                .build();
    }

    @Test
    void returnsBadRequestWhenMessageMissing() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"user-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestWhenUidMissing() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsReplyFromChatClient() throws Exception {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt(any(Prompt.class))
                .toolCallbacks(toolCallbackProvider)
                .call()
                .content())
                .thenReturn("hello");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\",\"uid\":\"user-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("hello"));
    }
}
