package com.els.backend.controller;

import com.els.backend.dto.montecarlo.MonteCarloPortfolioRequest;
import com.els.backend.service.MonteCarloRequest;
import com.els.backend.service.MonteCarloResponse;
import com.els.backend.service.MonteCarloService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monte-carlo")
@CrossOrigin(origins = "${app.frontend-origin}")
public class MonteCarloController {
    private final MonteCarloService monteCarloService;
    public MonteCarloController(MonteCarloService monteCarloService) {
        this.monteCarloService = monteCarloService;
    }
    /**
     * POST /api/monte-carlo
     *
     * {
     *   "ticker":       "VFIAX",
     *   "principal":    10000,
     *   "timeYears":    10,
     *   "goalAmount":   25000,   // optional
     *   "nSimulations": 1000     // optional
     * }
     */
    @PostMapping
    public ResponseEntity<MonteCarloResponse> simulate(@RequestBody MonteCarloRequest request){
        MonteCarloResponse result = monteCarloService.simulate(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/portfolio")
    public ResponseEntity<MonteCarloResponse> simulatePortfolio(@RequestBody MonteCarloPortfolioRequest request) {
        return ResponseEntity.ok(monteCarloService.simulatePortfolio(request));
    }
}
