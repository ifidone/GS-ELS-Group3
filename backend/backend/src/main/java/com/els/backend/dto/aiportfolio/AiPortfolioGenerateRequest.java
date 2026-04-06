package com.els.backend.dto.aiportfolio;

/**
 * Request body for POST /api/ai-portfolio/generate.
 */
public record AiPortfolioGenerateRequest(
        double investmentAmount,
        int investmentYears,
        String riskTolerance
) {
}
