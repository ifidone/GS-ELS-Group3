package com.els.backend.service;
import org.springframework.stereotype.Service;
import java.util.*;

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
    private static final double VOLTATILITY_FACTOR = 0.5;
    private static final int DEFAULT_SIMULATIONS = 1000;
    private static final int MAX_PATHS_TO_STORE = 200;

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
        double deterministic FV = principal * Math.exp(deterministicRate * timeYears);
        response.setDeterministicFV(round(deterministicFV));

        //sigma for sampling: proportional to historical return
        double sigma = Math.abs(historicalReturn * VOLTATILITY_FACTOR);
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
                sampledPaths.add(path)
            }
        }
        //sort percentile calculation
        Arrays.sort(finalValues);

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
        return r;
    }
}