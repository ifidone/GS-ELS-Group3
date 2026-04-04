package com.els.backend.service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monte-carlo")
@CrossOrigin(origins = "*") //match CORS setup
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
}