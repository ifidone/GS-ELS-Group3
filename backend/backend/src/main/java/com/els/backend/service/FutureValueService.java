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
    public static FutureValueComputation computeFutureValue(String ticker, double principal, double timeYears) {
        String t = normalizeTicker(ticker);

        // Basic validation
        if (t.isEmpty() || principal <= 0 || timeYears <= 0) {
            return new FutureValueComputation(0.00, 0.00, 0.00);
        }

        // 1) Beta from Newton stock-beta API (returned as "data")
        double beta = BetaService.getBeta(t);

        // 2) Expected return rate from last-year performance (computed from Newton price history)
        double expectedReturnRate = NewtonService.getExpectedReturn(t);

        // 3) CAPM rate: r = rf + beta * (expectedReturnRate - rf)
        double capmRate = computeCapmRate(beta, expectedReturnRate);

        // 4) Future value: FV = principal * e^(r * timeYears)
        double futureValue = principal * Math.exp(capmRate * timeYears);
        return new FutureValueComputation(beta, expectedReturnRate, futureValue);
    }

    public static double computeCapmRate(double beta, double expectedReturnRate) {
        double raw = RISK_FREE_RATE + beta * (expectedReturnRate - RISK_FREE_RATE);
        return Math.max(RISK_FREE_RATE, raw);
    }

    public static java.util.Map<String, Double> computeTimeSeries(double principal,
                                                                  double beta,
                                                                  double expectedReturnRate,
                                                                  int maxYears) {
        if (principal <= 0 || maxYears < 0
                || !Double.isFinite(beta) || !Double.isFinite(expectedReturnRate)) {
            return java.util.Collections.emptyMap();
        }

        double capmRate = computeCapmRate(beta, expectedReturnRate);
        java.util.Map<String, Double> series = new java.util.LinkedHashMap<>();
        for (int year = 0; year <= maxYears; year += 1) {
            double value = principal * Math.exp(capmRate * year);
            series.put(Integer.toString(year), value);
        }
        return series;
    }

    // // Clean ticker input (null-safe, trim spaces, uppercase)
    private static String normalizeTicker(String ticker) {
        return (ticker == null) ? "" : ticker.trim().toUpperCase();
    }

    public record FutureValueComputation(
            double beta,
            double expectedReturn,
            double futureValue
    ) {
    }
}
