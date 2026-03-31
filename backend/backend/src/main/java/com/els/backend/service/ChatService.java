package com.els.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    @Value("${openai.api.key}")
    private String apiKey;

    public String processMessage(String userMessage) {

        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-4o-mini",
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", "You are a concise financial assistant. First, answer user's question in 1 simple, direct sentence. " +
                                            "Then, if the user asked a question regarding a concept, list fundamental concepts related to the question with a simple explanation, using 1 star. " +
                                            "Keep under 60 words. End with a polite follow-up question."
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", userMessage
                            )
                    )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/chat/completions",
                    entity,
                    Map.class
            );

            if (response.getBody() == null) {
                return "No response from AI.";
            }

            List<Map> choices = (List<Map>) response.getBody().get("choices");

            if (choices == null || choices.isEmpty()) {
                return "No valid response from AI.";
            }

            Map message = (Map) choices.get(0).get("message");

            return message.get("content").toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error: Unable to process your request right now.";
        }
    }
}
