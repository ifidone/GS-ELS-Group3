package com.els.backend.service;

import com.els.backend.database.funds.MutualFundStore;
import com.els.backend.dto.aiportfolio.AiPortfolioAllocationItem;
import com.els.backend.dto.aiportfolio.AiPortfolioGenerateRequest;
import com.els.backend.dto.aiportfolio.AiPortfolioGenerateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Builds a diversified allocation from {@link MutualFundStore} funds only, using Newton/Beta data
 * already used elsewhere in the app. Explanations are rule-based (investor-friendly), not an LLM.
 */
@Service
public class AiPortfolioBuilderService {

    private static final double RISK_FREE_RATE = 0.04;
    private static final int MIN_FUNDS = 4;
    private static final int MAX_FUNDS_LOW = 6;
    private static final int MAX_FUNDS_MED = 7;
    private static final int MAX_FUNDS_HIGH = 8;

    private final MutualFundStore mutualFundStore;

    public AiPortfolioBuilderService(MutualFundStore mutualFundStore) {
        this.mutualFundStore = mutualFundStore;
    }

    public AiPortfolioGenerateResponse generate(AiPortfolioGenerateRequest request) {
        if (request.investmentAmount() <= 0 || request.investmentYears() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount and years must be positive.");
        }
        RiskTier tier = RiskTier.from(request.riskTolerance());
        List<MutualFundStore.MutualFund> all = mutualFundStore.listAll().stream()
                .filter(f -> f.ticker() != null && !f.ticker().isBlank())
                .collect(Collectors.toList());
        if (all.size() < MIN_FUNDS) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Not enough funds in the database to build a portfolio.");
        }

        List<FundMetrics> enriched = fetchMetricsParallel(all);

        List<FundMetrics> picked = selectFunds(enriched, tier);
        if (picked.size() < MIN_FUNDS) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not select enough funds for this risk profile. Try another risk level.");
        }

        double[] weights = assignWeights(picked, tier);
        normalizeWeights(weights);

        double weightedEr = 0;
        double blendedCapm = 0;
        List<AiPortfolioAllocationItem> items = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            FundMetrics m = picked.get(i);
            double w = weights[i];
            weightedEr += w * m.expectedReturn();
            double capm = FutureValueService.computeCapmRate(m.beta(), m.expectedReturn());
            blendedCapm += w * capm;
        }

        double fv = request.investmentAmount() * Math.exp(blendedCapm * request.investmentYears());

        for (int i = 0; i < picked.size(); i++) {
            FundMetrics m = picked.get(i);
            double pct = round2(weights[i] * 100.0);
            items.add(new AiPortfolioAllocationItem(
                    m.fund().ticker(),
                    m.fund().name(),
                    m.fund().category(),
                    pct,
                    round2(m.beta()),
                    round2(m.expectedReturn())
            ));
        }

        String explanation = buildExplanation(tier, request.investmentYears(), items, blendedCapm, weightedEr);

        return new AiPortfolioGenerateResponse(
                tier.displayLabel,
                items,
                round2(weightedEr),
                round2(blendedCapm),
                round2(fv),
                explanation
        );
    }

    /**
     * Fetches beta + expected return per fund from Newton in parallel (bounded pool + overall deadline).
     * Sequential calls were too slow for large catalogs and could hang indefinitely without RestTemplate timeouts.
     */
    private List<FundMetrics> fetchMetricsParallel(List<MutualFundStore.MutualFund> all) {
        int n = all.size();
        int poolSize = Math.min(12, Math.max(4, n));
        ExecutorService ex = Executors.newFixedThreadPool(poolSize);
        try {
            List<CompletableFuture<FundMetrics>> futures = all.stream()
                    .map(f -> CompletableFuture.supplyAsync(
                            () -> {
                                String raw = f.ticker();
                                String t = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
                                double beta = BetaService.getBeta(t);
                                double er = NewtonService.getExpectedReturn(t);
                                int score = categoryRiskScore(f.category());
                                return new FundMetrics(f, beta, er, score);
                            },
                            ex))
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(75, TimeUnit.SECONDS);
            List<FundMetrics> out = new ArrayList<>(n);
            for (CompletableFuture<FundMetrics> cf : futures) {
                out.add(cf.join());
            }
            return out;
        } catch (TimeoutException e) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Loading market data for all funds took too long. Try again in a moment.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not load fund metrics. Try again later.");
        } finally {
            ex.shutdownNow();
        }
    }

    private List<FundMetrics> selectFunds(List<FundMetrics> enriched, RiskTier tier) {
        List<FundMetrics> sortedByRisk = enriched.stream()
                .sorted(Comparator.comparingInt((FundMetrics m) -> m.score).thenComparing(m -> m.fund().ticker()))
                .collect(Collectors.toList());

        return switch (tier) {
            case LOW -> pickLowRisk(sortedByRisk);
            case MEDIUM -> pickMediumRisk(sortedByRisk);
            case HIGH -> pickHighRisk(sortedByRisk);
        };
    }

    /** Prefer bonds, large blend, large growth; cap volatility. */
    private List<FundMetrics> pickLowRisk(List<FundMetrics> sortedByRiskAsc) {
        List<FundMetrics> pool = sortedByRiskAsc.stream()
                .filter(m -> m.score <= 2)
                .collect(Collectors.toList());
        if (pool.size() < MIN_FUNDS) {
            pool = new ArrayList<>(sortedByRiskAsc);
        }
        return pool.stream().limit(MAX_FUNDS_LOW).collect(Collectors.toList());
    }

    /** Span from moderate to moderately aggressive names. */
    private List<FundMetrics> pickMediumRisk(List<FundMetrics> sortedByRiskAsc) {
        int n = sortedByRiskAsc.size();
        int from = Math.max(0, n / 2 - 2);
        int to = Math.min(n, from + MAX_FUNDS_MED);
        List<FundMetrics> slice = new ArrayList<>(sortedByRiskAsc.subList(from, to));
        if (slice.size() < MIN_FUNDS) {
            slice = sortedByRiskAsc.stream().limit(MAX_FUNDS_MED).collect(Collectors.toList());
        }
        return slice;
    }

    /** Overweight higher score (mid/small/intl/growth). */
    private List<FundMetrics> pickHighRisk(List<FundMetrics> sortedByRiskAsc) {
        List<FundMetrics> pool = sortedByRiskAsc.stream()
                .filter(m -> m.score >= 2)
                .collect(Collectors.toList());
        if (pool.size() < MIN_FUNDS) {
            pool = new ArrayList<>(sortedByRiskAsc);
        }
        List<FundMetrics> reversed = new ArrayList<>(pool);
        reversed.sort(Comparator.comparingInt((FundMetrics m) -> m.score).reversed()
                .thenComparing(m -> m.fund().ticker()));
        return reversed.stream().limit(MAX_FUNDS_HIGH).collect(Collectors.toList());
    }

    private double[] assignWeights(List<FundMetrics> picked, RiskTier tier) {
        int n = picked.size();
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) {
            FundMetrics m = picked.get(i);
            raw[i] = switch (tier) {
                case LOW -> 5.0 - m.score + 0.05;
                case MEDIUM -> 1.0;
                case HIGH -> m.score + 0.05;
            };
            raw[i] = Math.max(raw[i], 0.01);
        }
        return raw;
    }

    private void normalizeWeights(double[] w) {
        double s = 0;
        for (double v : w) {
            s += v;
        }
        if (s <= 0) {
            double eq = 1.0 / w.length;
            for (int i = 0; i < w.length; i++) {
                w[i] = eq;
            }
            return;
        }
        for (int i = 0; i < w.length; i++) {
            w[i] /= s;
        }
    }

    private String buildExplanation(
            RiskTier tier,
            int years,
            List<AiPortfolioAllocationItem> items,
            double blendedCapm,
            double weightedEr
    ) {
        String names = items.stream()
                .limit(3)
                .map(AiPortfolioAllocationItem::ticker)
                .collect(Collectors.joining(", "));
        if (items.size() > 3) {
            names += ", …";
        }

        String horizon = years >= 10
                ? "A longer horizon like yours can usually absorb more short-term swings, so we leaned into diversified equity sleeves while still matching your stated risk."
                : years >= 5
                ? "For a medium-term horizon we balanced growth potential with diversification across market caps and regions."
                : "For a shorter horizon we kept the mix relatively defensive while still using only funds available in this platform.";

        String risk = switch (tier) {
            case LOW -> "Low risk prioritizes steadier categories (for example bonds and broad large-cap index funds) when those names exist in the fund list.";
            case MEDIUM -> "Medium risk spreads exposure across large, mid, and international names so growth and stability are both represented.";
            case HIGH -> "Higher risk tilts toward growth-oriented and smaller-cap funds where available, which historically carry more volatility but more upside potential.";
        };

        return String.format(
                "%s %s "
                        + "We used only funds returned by the live fund catalog and each fund's historical return and beta from the same data sources as the calculator. "
                        + "The blended annual rate used for the headline projection is about %.2f%% (weighted CAPM-style mix); the simple weighted average of raw historical returns is about %.2f%%. "
                        + "This is educational projection—not personal advice. Holdings preview: %s.",
                risk,
                horizon,
                blendedCapm * 100.0,
                weightedEr * 100.0,
                names
        );
    }

    /**
     * Higher score = more aggressive. Derived from mutual_funds.category strings in the DB.
     */
    static int categoryRiskScore(String category) {
        if (category == null) {
            return 2;
        }
        String c = category.toLowerCase(Locale.ROOT);
        if (c.contains("bond")) {
            return 0;
        }
        if (c.contains("large") && c.contains("blend")) {
            return 1;
        }
        if (c.contains("large") && c.contains("growth")) {
            return 2;
        }
        if (c.contains("international") || c.contains("global") || c.contains("emerging")) {
            return 3;
        }
        if (c.contains("mid")) {
            return 3;
        }
        if (c.contains("small")) {
            return 4;
        }
        return 2;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record FundMetrics(MutualFundStore.MutualFund fund, double beta, double expectedReturn, int score) {
    }

    private enum RiskTier {
        LOW("Low"),
        MEDIUM("Medium"),
        HIGH("High");

        final String displayLabel;

        RiskTier(String displayLabel) {
            this.displayLabel = displayLabel;
        }

        static RiskTier from(String raw) {
            if (raw == null || raw.isBlank()) {
                return MEDIUM;
            }
            String u = raw.trim().toUpperCase(Locale.ROOT);
            if (u.equals("LOW") || u.equals("L")) {
                return LOW;
            }
            if (u.equals("HIGH") || u.equals("H") || u.equals("AGGRESSIVE")) {
                return HIGH;
            }
            return MEDIUM;
        }
    }
}
