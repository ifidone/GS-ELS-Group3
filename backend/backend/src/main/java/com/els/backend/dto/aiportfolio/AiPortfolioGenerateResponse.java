package com.els.backend.dto.aiportfolio;

import java.util.List;

public record AiPortfolioGenerateResponse(
        String riskLabel,
        List<AiPortfolioAllocationItem> allocations,
        /** Arithmetic weighted average of funds' historical expected returns (Newton). */
        double portfolioWeightedExpectedReturn,
        /** Blended CAPM-style annual rate used for deterministic FV (Σ wᵢ × capmRateᵢ). */
        double portfolioBlendedAnnualRate,
        double deterministicFutureValue,
        String explanation
) {
}
