package com.els.backend.service;

import com.els.backend.dto.montecarlo.MonteCarloPortfolioRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Monte Carlo simulation for the mutual fund calculator.
 *
 * Uses the EXACT same formula as FutureValueService:
 *   r = RISK_FREE_RATE + beta * (expectedReturn - RISK_FREE_RATE)
 *   FV = principal * e^(r * t)
 *
 * Each simulation draws a randomised expectedReturn from:
 *   N(historicalExpectedReturn, sigma²)
 * where sigma is estimated as |historicalExpectedReturn * VOLATILITY_FACTOR|.
 *
 * Beta is fixed (fetched once from Newton API, same as FutureValueService).
 * Does NOT import or modify any existing class — purely additive.
 */

@Service
public class MonteCarloService{
    private static final double RISK_FREE_RATE = 0.04;
    // Controls how much expectedReturn varies across simulations.
    // 0.5 means std dev = 50% of the historical expected return.
    // e.g. if expectedReturn = 0.12, sigma = 0.06
    private static final double VOLATILITY_FACTOR = 0.5;
    private static final int DEFAULT_SIMULATIONS = 1000;
    private static final int MAX_PATHS_TO_STORE = 200;
    private static final double INFLATION_RATE = 0.03; // 3% average US inflation

    public MonteCarloResponse simulate(MonteCarloRequest req) {
        //resolve + validate inputs
        String ticker = req.getTicker() == null ? "" : req.getTicker().trim().toUpperCase();
        double principal = req.getPrincipal();
        double timeYears = req.getTimeYears();
        double goal = req.getGoalAmount();
        int n = req.getNSimulations() > 0 ? req.getNSimulations() : DEFAULT_SIMULATIONS;

        MonteCarloResponse response = new MonteCarloResponse();
        response.setTicker(ticker);
        response.setPrincipal(principal);
        response.setTimeYears(timeYears);

        if (ticker.isEmpty() || principal <= 0 || timeYears <= 0) {
            return buildEmptyResponse(response, goal);
        }
        //fetch beta + historical expected return
        double beta = BetaService.getBeta(ticker);
        double historicalReturn = NewtonService.getExpectedReturn(ticker);
        response.setBeta(beta);
        response.setExpectedReturn(historicalReturn);

        //deterministic FV for comparison
        double deterministicRate = RISK_FREE_RATE + beta * (historicalReturn - RISK_FREE_RATE);
        double deterministicFV = principal * Math.exp(deterministicRate * timeYears);
        response.setDeterministicFV(round(deterministicFV));

        //sigma for sampling: proportional to historical return
        double sigma = Math.abs(historicalReturn * VOLATILITY_FACTOR);
        //floor sigma so there's even spread if historicalReturn ~ 0
        if (sigma < 0.01) sigma = 0.01;

        //run N simulations
        Random rng = new Random();
        double[] finalValues = new double[n];
        List<List<Double>> sampledPaths = new ArrayList<>();

        for(int i = 0; i < n; i++){
            //sample random expectedreturn for this simulation
            double sampledReturn = historicalReturn + sigma * rng.nextGaussian();
            //CAPM rate
            double r = RISK_FREE_RATE + beta * (sampledReturn - RISK_FREE_RATE);
            //build year by year path
            List<Double> path = new ArrayList<>(((int) timeYears) + 1);
            path.add(round(principal)); //year 0 = initial investment

            for (int y = 1; y <= (int) timeYears; y++){
                double yearFV = principal * Math.exp(r*y);
                path.add(round(Math.max(yearFV, 0)));
            }
            finalValues[i] = path.get(path.size() - 1);
            if (i < MAX_PATHS_TO_STORE) {
                sampledPaths.add(path);
            }
        }
        //sort percentile calculation
        Arrays.sort(finalValues);

        double sharpeRatio = round((historicalReturn - RISK_FREE_RATE) / Math.max(Math.abs(historicalReturn * VOLATILITY_FACTOR), 0.001));

        double riskAdjustedReturn = round(historicalReturn / (beta > 0 ? beta : 1));

        double breakevenYears = round(Math.log(2) /
                (deterministicRate > 0 ? deterministicRate : 0.01));

        double inflationAdjustedFV = round(principal *
                Math.exp((deterministicRate - INFLATION_RATE) * timeYears));

        double valueAtRisk = round(principal - percentile(finalValues, 5));

        double p10 = percentile(finalValues, 10);
        double p25 = percentile(finalValues, 25);
        double p50 = percentile(finalValues, 50);
        double p75 = percentile(finalValues, 75);
        double p90 = percentile(finalValues, 90);

        response.setPercentile10(p10);
        response.setPercentile25(p25);
        response.setPercentile50(p50);
        response.setPercentile75(p75);
        response.setPercentile90(p90);

        response.setWorstCase(p10);
        response.setMedianCase(p50);
        response.setBestCase(p90);

        response.setSharpeRatio(sharpeRatio);
        response.setRiskAdjustedReturn(riskAdjustedReturn);
        response.setBreakevenYears(breakevenYears);
        response.setInflationAdjustedFV(inflationAdjustedFV);
        response.setValueAtRisk(valueAtRisk);

        //prob of reaching goal
        if (goal > 0) {
            long hits = 0;
            for (double v : finalValues) if (v >= goal) hits++;
            response.setProbabilityOfGoal(round((double) hits / n));
        } else{
            response.setProbabilityOfGoal(-1);
        }
        response.setYearlyPaths(sampledPaths);
        return response;
    }

    /**
     * Portfolio mode: weighted expected return and volatility (independent fund variance approximation),
     * then same sampling pattern as {@link #simulate(MonteCarloRequest)} using portfolio-level beta and mean ER.
     */
    public MonteCarloResponse simulatePortfolio(MonteCarloPortfolioRequest req) {
        double principal = req.principal();
        double timeYears = req.timeYears();
        double goal = req.goalAmount();
        int n = req.nSimulations() > 0 ? req.nSimulations() : DEFAULT_SIMULATIONS;
        List<MonteCarloPortfolioRequest.PortfolioHolding> holdings = req.holdings();

        MonteCarloResponse response = new MonteCarloResponse();
        response.setTicker("PORTFOLIO");
        response.setPrincipal(principal);
        response.setTimeYears(timeYears);

        if (holdings == null || holdings.isEmpty() || principal <= 0 || timeYears <= 0) {
            return buildEmptyResponse(response, goal);
        }

        double sumW = 0;
        List<MonteCarloPortfolioRequest.PortfolioHolding> active = new ArrayList<>();
        for (MonteCarloPortfolioRequest.PortfolioHolding h : holdings) {
            if (h.ticker() == null || h.ticker().isBlank() || h.weight() <= 0) {
                continue;
            }
            sumW += h.weight();
            active.add(h);
        }
        if (sumW < 0.98 || sumW > 1.02 || active.isEmpty()) {
            return buildEmptyResponse(response, goal);
        }

        LinkedHashSet<String> tickers = new LinkedHashSet<>();
        for (MonteCarloPortfolioRequest.PortfolioHolding h : active) {
            tickers.add(h.ticker().trim().toUpperCase(Locale.ROOT));
        }
        Map<String, double[]> metrics = fetchBetaAndErParallel(tickers);

        double betaP = 0;
        double muP = 0;
        double varP = 0;
        for (MonteCarloPortfolioRequest.PortfolioHolding h : active) {
            String t = h.ticker().trim().toUpperCase(Locale.ROOT);
            double w = h.weight() / sumW;
            double[] be = metrics.get(t);
            double beta = be != null ? be[0] : 0;
            double er = be != null ? be[1] : 0;
            betaP += w * beta;
            muP += w * er;
            double sigma = Math.abs(er * VOLATILITY_FACTOR);
            if (sigma < 0.01) {
                sigma = 0.01;
            }
            varP += w * w * sigma * sigma;
        }
        double sigmaP = Math.sqrt(Math.max(varP, 1e-6));
        if (sigmaP < 0.01) {
            sigmaP = 0.01;
        }

        response.setBeta(round(betaP));
        response.setExpectedReturn(round(muP));

        double deterministicRate = RISK_FREE_RATE + betaP * (muP - RISK_FREE_RATE);
        double deterministicFV = principal * Math.exp(deterministicRate * timeYears);
        response.setDeterministicFV(round(deterministicFV));

        Random rng = new Random();
        double[] finalValues = new double[n];
        List<List<Double>> sampledPaths = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            double sampledReturn = muP + sigmaP * rng.nextGaussian();
            double r = RISK_FREE_RATE + betaP * (sampledReturn - RISK_FREE_RATE);
            List<Double> path = new ArrayList<>(((int) timeYears) + 1);
            path.add(round(principal));
            for (int y = 1; y <= (int) timeYears; y++) {
                double yearFv = principal * Math.exp(r * y);
                path.add(round(Math.max(yearFv, 0)));
            }
            finalValues[i] = path.get(path.size() - 1);
            if (i < MAX_PATHS_TO_STORE) {
                sampledPaths.add(path);
            }
        }

        Arrays.sort(finalValues);
        double sharpeRatio = round((muP - RISK_FREE_RATE) / Math.max(sigmaP, 0.001));
        double riskAdjustedReturn = round(muP / (betaP > 0 ? betaP : 1));
        double breakevenYears = round(Math.log(2) / (deterministicRate > 0 ? deterministicRate : 0.01));
        double inflationAdjustedFV = round(principal * Math.exp((deterministicRate - INFLATION_RATE) * timeYears));
        double valueAtRisk = round(principal - percentile(finalValues, 5));

        response.setPercentile10(percentile(finalValues, 10));
        response.setPercentile25(percentile(finalValues, 25));
        response.setPercentile50(percentile(finalValues, 50));
        response.setPercentile75(percentile(finalValues, 75));
        response.setPercentile90(percentile(finalValues, 90));
        response.setWorstCase(percentile(finalValues, 10));
        response.setMedianCase(percentile(finalValues, 50));
        response.setBestCase(percentile(finalValues, 90));
        response.setSharpeRatio(sharpeRatio);
        response.setRiskAdjustedReturn(riskAdjustedReturn);
        response.setBreakevenYears(breakevenYears);
        response.setInflationAdjustedFV(inflationAdjustedFV);
        response.setValueAtRisk(valueAtRisk);

        if (goal > 0) {
            long hits = 0;
            for (double v : finalValues) {
                if (v >= goal) {
                    hits++;
                }
            }
            response.setProbabilityOfGoal(round((double) hits / n));
        } else {
            response.setProbabilityOfGoal(-1);
        }
        response.setYearlyPaths(sampledPaths);
        return response;
    }

    private Map<String, double[]> fetchBetaAndErParallel(Set<String> tickers) {
        if (tickers.isEmpty()) {
            return Map.of();
        }
        Map<String, double[]> out = new ConcurrentHashMap<>();
        int pool = Math.min(8, Math.max(1, tickers.size()));
        ExecutorService ex = Executors.newFixedThreadPool(pool);
        try {
            List<CompletableFuture<Void>> cfs = tickers.stream()
                    .map(t -> CompletableFuture.runAsync(
                            () -> {
                                double beta = BetaService.getBeta(t);
                                double er = NewtonService.getExpectedReturn(t);
                                out.put(t, new double[] {beta, er});
                            },
                            ex))
                    .collect(Collectors.toList());
            CompletableFuture.allOf(cfs.toArray(CompletableFuture[]::new))
                    .get(40, TimeUnit.SECONDS);
            return out;
        } catch (TimeoutException e) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT, "Market data fetch timed out. Try again.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Could not load data for portfolio simulation.");
        } finally {
            ex.shutdownNow();
        }
    }

    //linear interpolation percentile on presorted arr
    private double percentile(double[] sorted, double p){
        if(sorted.length == 0) return 0;
        double index = (p/100.0) * (sorted.length - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return round(sorted[lower]);
        double w = index - lower;
        return round(sorted[lower] * (1-w) + sorted[upper] * w);
    }
    //round 2 decimal points
    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
    //zeroed-out response for invalid inputs
    private MonteCarloResponse buildEmptyResponse(MonteCarloResponse r, double goal) {
        r.setPercentile10(0); r.setPercentile25(0); r.setPercentile50(0);
        r.setPercentile75(0); r.setPercentile90(0);
        r.setWorstCase(0);    r.setMedianCase(0);   r.setBestCase(0);
        r.setDeterministicFV(0);
        r.setProbabilityOfGoal(goal > 0 ? 0 : -1);
        r.setYearlyPaths(Collections.emptyList());
        r.setSharpeRatio(0);
        r.setRiskAdjustedReturn(0);
        r.setBreakevenYears(0);
        r.setInflationAdjustedFV(0);
        r.setValueAtRisk(0);
        return r;
    }
}