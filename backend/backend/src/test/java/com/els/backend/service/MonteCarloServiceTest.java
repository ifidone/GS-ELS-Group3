package com.els.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloServiceTest {

    private MonteCarloService service;

    @BeforeEach
    void setUp() {
        service = new MonteCarloService();
    }

    // --- 1. Basic sanity check ---
    // Valid inputs should return non-zero results
    @Test
    void validInputs_returnsNonZeroResults() {
        MonteCarloRequest req = buildRequest("VFIAX", 10000, 10, 0, 1000);
        MonteCarloResponse res = service.simulate(req);

        assertTrue(res.getMedianCase()  > 0, "Median should be positive");
        assertTrue(res.getWorstCase()   > 0, "Worst case should be positive");
        assertTrue(res.getBestCase()    > 0, "Best case should be positive");
        assertTrue(res.getDeterministicFV() > 0, "Deterministic FV should be positive");
    }

    // --- 2. Check scenario ordering ---
    // worst <= median <= best always
    @Test
    void scenarios_areInCorrectOrder() {
        MonteCarloRequest req = buildRequest("VFIAX", 10000, 10, 0, 1000);
        MonteCarloResponse res = service.simulate(req);

        assertTrue(res.getWorstCase()  <= res.getMedianCase(), "Worst should be <= Median");
        assertTrue(res.getMedianCase() <= res.getBestCase(),   "Median should be <= Best");
    }

    // --- 3. Percentile ordering ---
    // p10 <= p25 <= p50 <= p75 <= p90
    @Test
    void percentiles_areInCorrectOrder() {
        MonteCarloRequest req = buildRequest("VFIAX", 10000, 10, 0, 1000);
        MonteCarloResponse res = service.simulate(req);

        assertTrue(res.getPercentile10() <= res.getPercentile25());
        assertTrue(res.getPercentile25() <= res.getPercentile50());
        assertTrue(res.getPercentile50() <= res.getPercentile75());
        assertTrue(res.getPercentile75() <= res.getPercentile90());
    }

    // --- 4. No goal set → probabilityOfGoal should be -1 ---
    @Test
    void noGoal_returnsMinus1() {
        MonteCarloRequest req = buildRequest("VFIAX", 10000, 10, 0, 1000);
        MonteCarloResponse res = service.simulate(req);

        assertEquals(-1.0, res.getProbabilityOfGoal(), "Should be -1 when no goal set");
    }

    // --- 5. Goal set → probability between 0 and 1 ---
    @Test
    void withGoal_probabilityIsBetween0And1() {
        MonteCarloRequest req = buildRequest("VFIAX", 10000, 10, 25000, 1000);
        MonteCarloResponse res = service.simulate(req);

        assertTrue(res.getProbabilityOfGoal() >= 0.0 && res.getProbabilityOfGoal() <= 1.0,
                "Probability must be between 0 and 1");
    }

    // --- 6. Yearly paths are returned ---
    @Test
    void yearlyPaths_areNotEmpty() {
        MonteCarloRequest req = buildRequest("VFIAX", 10000, 10, 0, 1000);
        MonteCarloResponse res = service.simulate(req);

        assertNotNull(res.getYearlyPaths(),          "Paths should not be null");
        assertFalse(res.getYearlyPaths().isEmpty(),  "Paths should not be empty");

        // Each path should have (years + 1) entries: year 0 through year 10
        assertEquals(11, res.getYearlyPaths().get(0).size(), "Path length should be years + 1");
    }

    // --- 7. First value in every path equals the principal ---
    @Test
    void yearlyPaths_startWithPrincipal() {
        MonteCarloRequest req = buildRequest("VFIAX", 10000, 10, 0, 1000);
        MonteCarloResponse res = service.simulate(req);

        res.getYearlyPaths().forEach(path ->
                assertEquals(10000.0, path.get(0), "Year 0 should always equal principal")
        );
    }

    // --- 8. Invalid inputs → zeroed out response, no crash ---
    @Test
    void invalidInputs_returnsZeroedResponse() {
        MonteCarloRequest req = buildRequest("", -1000, -5, 0, 1000);
        MonteCarloResponse res = service.simulate(req);

        assertEquals(0.0, res.getMedianCase(),       "Median should be 0 for invalid input");
        assertEquals(0.0, res.getDeterministicFV(),  "DeterministicFV should be 0 for invalid input");
    }

    // --- Helper to build requests quickly ---
    private MonteCarloRequest buildRequest(String ticker, double principal,
                                           double years, double goal, int n) {
        MonteCarloRequest req = new MonteCarloRequest();
        req.setTicker(ticker);
        req.setPrincipal(principal);
        req.setTimeYears(years);
        req.setGoalAmount(goal);
        req.setNSimulations(n);
        return req;
    }
}