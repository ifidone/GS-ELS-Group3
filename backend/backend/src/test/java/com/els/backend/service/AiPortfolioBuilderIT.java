package com.els.backend.service;

import com.els.backend.dto.aiportfolio.AiPortfolioGenerateRequest;
import com.els.backend.dto.aiportfolio.AiPortfolioGenerateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AiPortfolioBuilderIT {

    @Autowired
    private AiPortfolioBuilderService aiPortfolioBuilderService;

    @Test
    void generate_returnsAllocationsAgainstLiveDbAndNewton() {
        AiPortfolioGenerateRequest req = new AiPortfolioGenerateRequest(25_000, 10, "MEDIUM");
        AiPortfolioGenerateResponse res = aiPortfolioBuilderService.generate(req);
        assertNotNull(res.allocations());
        assertTrue(res.allocations().size() >= 4, "expected at least 4 funds");
        assertFalse(res.explanation().isBlank());
    }
}
