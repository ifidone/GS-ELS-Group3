package com.els.backend.service;
import org.springframework.stereotype.Service;

@Service
public class FutureValueService {

    // Hardcoded per requirements
    private static final double RISK_FREE_RATE = 0.04;

    /**
     * Computes future value using  formulas:
     * expectedReturnRate = (lastDayValue - firstDayValue) / firstDayValue
     * r (capmRate) = riskFreeRate + beta * (expectedReturnRate - riskFreeRate)
     * FV = principal * e^(r * timeYears)
     */
    public static double computeFutureValue(String ticker, double principal, double timeYears) {
        String t = normalizeTicker(ticker);

        // Basic validation
        if (t.isEmpty() || principal <= 0 || timeYears <= 0) {
            return 0.00;
        }

        // 1) Beta from Newton stock-beta API (returned as "data")
        double beta = BetaService.getBeta(t);

        // 2) Expected return rate from last-year performance (computed from Newton price history)
        double expectedReturnRate = NewtonService.getExpectedReturn(t);

        // 3) CAPM rate: r = rf + beta * (expectedReturnRate - rf)
        double capmRate = RISK_FREE_RATE + beta * (expectedReturnRate - RISK_FREE_RATE);

        // 4) Future value: FV = principal * e^(r * timeYears)
        return principal * Math.exp(capmRate * timeYears);
    }

    // // Clean ticker input (null-safe, trim spaces, uppercase)
    private static String normalizeTicker(String ticker) {
        return (ticker == null) ? "" : ticker.trim().toUpperCase();
    }
}