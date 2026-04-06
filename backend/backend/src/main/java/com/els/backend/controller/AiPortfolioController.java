package com.els.backend.controller;

import com.els.backend.dto.aiportfolio.AiPortfolioGenerateRequest;
import com.els.backend.dto.aiportfolio.AiPortfolioGenerateResponse;
import com.els.backend.service.AiPortfolioBuilderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-portfolio")
@CrossOrigin(origins = "${app.frontend-origin}")
public class AiPortfolioController {

    private final AiPortfolioBuilderService aiPortfolioBuilderService;

    public AiPortfolioController(AiPortfolioBuilderService aiPortfolioBuilderService) {
        this.aiPortfolioBuilderService = aiPortfolioBuilderService;
    }

    @PostMapping("/generate")
    public ResponseEntity<AiPortfolioGenerateResponse> generate(@RequestBody AiPortfolioGenerateRequest request) {
        return ResponseEntity.ok(aiPortfolioBuilderService.generate(request));
    }
}
