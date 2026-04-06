package com.els.backend.dto.aiportfolio;

public record AiPortfolioAllocationItem(
        String ticker,
        String name,
        String category,
        double weightPercent,
        double beta,
        double expectedReturn
) {
}
