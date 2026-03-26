package com.els.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatClient.Builder chatClientBuilder;

    @MockBean
    private SyncMcpToolCallbackProvider toolCallbackProvider;

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
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
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
