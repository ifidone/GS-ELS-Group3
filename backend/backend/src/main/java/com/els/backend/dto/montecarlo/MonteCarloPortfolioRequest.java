package com.els.backend.dto.montecarlo;

import java.util.List;

/**
 * Request for POST /api/monte-carlo/portfolio — weighted holdings must sum to ~1.0.
 */
public record MonteCarloPortfolioRequest(
        double principal,
        double timeYears,
        double goalAmount,
        int nSimulations,
        List<PortfolioHolding> holdings
) {
    public record PortfolioHolding(String ticker, double weight) {
    }
}
