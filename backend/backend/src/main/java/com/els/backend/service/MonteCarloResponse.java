package com.els.backend.service;

import java.util.List;

public class MonteCarloResponse {
    private String ticker;
    private double principal;
    private double timeYears;
    private double beta;
    private double expectedReturn;

    private double percentile10; //worst case
    private double percentile25;
    private double percentile50; //median
    private double percentile75;
    private double percentile90; //best case

    private double worstCase;
    private double medianCase;
    private double bestCase;

    private double deterministicFV; //(same as futurevalueservice) for comparison
    private double probabilityOfGoal; //(-1 if no goal set)

    //year by year paths for fan chart - up to 200 sampled paths
    //yearlyPaths[i][y] = portfolio value at the end of year y for simulation i
    private List<List<Double>> yearlyPaths;

    private double sharpeRatio;
    private double riskAdjustedReturn;
    private double breakevenYears;
    private double inflationAdjustedFV;
    private double valueAtRisk;

    // --- Getters & Setters ---
    public String getTicker()                         { return ticker; }
    public void   setTicker(String v)                 { this.ticker = v; }

    public double getPrincipal()                      { return principal; }
    public void   setPrincipal(double v)              { this.principal = v; }

    public double getTimeYears()                      { return timeYears; }
    public void   setTimeYears(double v)              { this.timeYears = v; }

    public double getBeta()                           { return beta; }
    public void   setBeta(double v)                   { this.beta = v; }

    public double getExpectedReturn()                 { return expectedReturn; }
    public void   setExpectedReturn(double v)         { this.expectedReturn = v; }

    public double getPercentile10()                   { return percentile10; }
    public void   setPercentile10(double v)           { this.percentile10 = v; }

    public double getPercentile25()                   { return percentile25; }
    public void   setPercentile25(double v)           { this.percentile25 = v; }

    public double getPercentile50()                   { return percentile50; }
    public void   setPercentile50(double v)           { this.percentile50 = v; }

    public double getPercentile75()                   { return percentile75; }
    public void   setPercentile75(double v)           { this.percentile75 = v; }

    public double getPercentile90()                   { return percentile90; }
    public void   setPercentile90(double v)           { this.percentile90 = v; }

    public double getWorstCase()                      { return worstCase; }
    public void   setWorstCase(double v)              { this.worstCase = v; }

    public double getMedianCase()                     { return medianCase; }
    public void   setMedianCase(double v)             { this.medianCase = v; }

    public double getBestCase()                       { return bestCase; }
    public void   setBestCase(double v)               { this.bestCase = v; }

    public double getDeterministicFV()                { return deterministicFV; }
    public void   setDeterministicFV(double v)        { this.deterministicFV = v; }

    public double getProbabilityOfGoal()              { return probabilityOfGoal; }
    public void   setProbabilityOfGoal(double v)      { this.probabilityOfGoal = v; }

    public List<List<Double>> getYearlyPaths()        { return yearlyPaths; }
    public void setYearlyPaths(List<List<Double>> v)  { this.yearlyPaths = v; }

    public double getSharpeRatio()                  { return sharpeRatio; }
    public void   setSharpeRatio(double v)          { this.sharpeRatio = v; }

    public double getRiskAdjustedReturn()           { return riskAdjustedReturn; }
    public void   setRiskAdjustedReturn(double v)   { this.riskAdjustedReturn = v; }

    public double getBreakevenYears()               { return breakevenYears; }
    public void   setBreakevenYears(double v)       { this.breakevenYears = v; }

    public double getInflationAdjustedFV()          { return inflationAdjustedFV; }
    public void   setInflationAdjustedFV(double v)  { this.inflationAdjustedFV = v; }

    public double getValueAtRisk()                  { return valueAtRisk; }
    public void   setValueAtRisk(double v)          { this.valueAtRisk = v; }
}