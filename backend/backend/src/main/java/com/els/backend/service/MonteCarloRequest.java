package com.els.backend.service;

public class MonteCarloRequest {
    private String ticker;
    private double principal;
    private double timeYears;
    private double goalAmount; //optional, not set = 0
    private int nSimulations; //options, default = 1000

    public String getTicker() {return ticker;}
    public void setTicker(String v) {this.ticker = v;}

    public double getPrincipal() {return principal;}
    public void setPrincipal(double v) {this.principal = v;}

    public double getTimeYears() {return timeYears;}
    public void setTimeYears(double v) {this.timeYears = v;}

    public double getGoalAmount() {return goalAmount;}
    public void setGoalAmount(double v) {this.goalAmount = v;}

    public int getNSimulations() {return nSimulations;}
    public void setNSimulations(int v) { this.nSimulations = v;}
}
