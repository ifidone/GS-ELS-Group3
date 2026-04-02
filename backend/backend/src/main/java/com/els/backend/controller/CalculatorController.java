package com.els.backend.controller;

import com.els.backend.service.FutureValueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calculator")
@CrossOrigin(origins = "${app.frontend-origin}")
public class CalculatorController {

    // Frontend (calculator.ts) sends ticker, initialInvestment, and years to this endpoint.
    private static final int TIME_SERIES_YEARS = 20;

    @PostMapping("/project")
    public ResponseEntity<CalculatorProjectionResponse> project(@RequestBody CalculatorProjectionRequest request) {
        // Basic request validation so backend services receive clean input.
        if (request == null || request.ticker() == null || request.ticker().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (request.initialInvestment() <= 0 || request.years() <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Normalize ticker once before passing it to service layer.
        String ticker = request.ticker().trim().toUpperCase();

        // Single orchestration call: FutureValueService computes and returns
        // beta, expectedReturn, and futureValue in one structured response.
        FutureValueService.FutureValueComputation computation = FutureValueService.computeFutureValue(
                ticker,
                request.initialInvestment(),
                request.years()
        );

        // Map backend computation result into API response consumed by calculator.ts.
        CalculatorProjectionResponse response = new CalculatorProjectionResponse(
                ticker,
                request.initialInvestment(),
                request.years(),
                computation.beta(),
                computation.expectedReturn(),
                computation.futureValue(),
                FutureValueService.computeTimeSeries(
                        request.initialInvestment(),
                        computation.beta(),
                        computation.expectedReturn(),
                        TIME_SERIES_YEARS
                )
        );

        return ResponseEntity.ok(response);
    }

    public record CalculatorProjectionRequest(
            String ticker,
            double initialInvestment,
            double years
    ) {
    }

    // Response payload sent back to frontend calculator with full projection details.
    public record CalculatorProjectionResponse(
            String ticker,
            double initialInvestment,
            double years,
            double beta,
            double expectedReturn,
            double futureValue,
            java.util.Map<String, Double> timeSeries
    ) {
    }
}
