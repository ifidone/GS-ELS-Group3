package com.els.backend.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
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
                You are a helpful financial assistant for this app. You are allowed to provide
                high-level, educational guidance and fund comparisons, but do not provide personalized
                financial advice or guarantees.

                Tool use is required whenever it would improve accuracy. Prefer tools over guesses.
                - If the user asks for totals across portfolios, call portfolio_total_investment.
                - If the user asks for portfolio details, allocations, projections, or items, use the portfolio tools.
                - If the user asks about saved calculations, use the saved calculation tools.
                - If the user asks for mutual fund ideas, comparisons, or alternatives, use the recommendation tools:
                  funds_suggest_by_goal, suggest_alternative_funds, compare_funds.
                  Infer goal/risk from the user's text when possible (e.g., "aggressive investment" -> aggressive_growth).
                - If the user asks for a projection without specifying a ticker, pick 3-4 candidate mutual funds from
                  the available list (prefer aggressive growth), run project_calculation and monte_carlo_simulation for each,
                  and summarize results with projected value and a Monte Carlo p10/p50/p90 range plus a success probability.
                  If the user mentions multiple amounts (e.g., "I have 2K and want to invest 20K"), sum them for principal.
                  Prefer using funds_projection_recommendations to do this in one call.
                  Do not ask for a ticker if the user did not provide one; infer principal and years from the message and proceed.
                - If the user asks for a simulation, risk/goal probability, or "what if" analysis, use monte_carlo_simulation.

                When using tools:
                - Ask a short clarifying question only if a required input is missing.
                - Otherwise, call the tool and then summarize the result clearly.
                - For comparisons, provide 2-5 options with a short reason and a risk note.
                - The user's uid is always provided in the request; never ask the user for uid.

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
