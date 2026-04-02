package com.els.backend.controller;

import com.els.backend.database.auth.AuthStore;
import com.els.backend.database.calculations.SavedCalculationStore;
import com.els.backend.service.FirebaseAuthService;
import com.google.firebase.auth.FirebaseAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${app.frontend-origin}")
public class SavedCalculationsController {

    // CRUD endpoints for saved calculator runs tied to Firebase-authenticated users.
    private static final Logger logger = LoggerFactory.getLogger(SavedCalculationsController.class);
    private static final int TIME_SERIES_YEARS = 20;
    private final FirebaseAuthService firebaseAuthService;
    private final AuthStore authStore;
    private final SavedCalculationStore savedCalculationStore;

    public SavedCalculationsController(
            FirebaseAuthService firebaseAuthService,
            AuthStore authStore,
            SavedCalculationStore savedCalculationStore
    ) {
        this.firebaseAuthService = firebaseAuthService;
        this.authStore = authStore;
        this.savedCalculationStore = savedCalculationStore;
    }

    @GetMapping("/calculations")
    public ResponseEntity<List<SavedCalculationResponse>> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) UidRequest request
    ) {
        String uid = resolveUid(authorizationHeader, request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<SavedCalculationResponse> responses = savedCalculationStore.listByUid(uid)
                .stream()
                .map(SavedCalculationResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/calculations")
    public ResponseEntity<SavedCalculationResponse> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) SavedCalculationRequest request
    ) {
        // Validate input and persist a new saved calculation for the caller.
        String uid = resolveUid(authorizationHeader, request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!isValidRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        SavedCalculationStore.SavedCalculationPayload payload = request.toPayload();
        FirebaseAuthService.VerifiedFirebaseUser user = verifyUser(authorizationHeader);
        if (user != null) {
            authStore.insertIfMissing(user);
        } else {
            authStore.insertIfMissingUid(uid);
        }
        SavedCalculationStore.SavedCalculation saved = savedCalculationStore.insert(
                uid,
                payload
        );
        return ResponseEntity.ok(SavedCalculationResponse.from(saved));
    }

    @PutMapping("/calculations/{id}")
    public ResponseEntity<SavedCalculationResponse> update(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable("id") long id,
            @RequestBody(required = false) SavedCalculationRequest request
    ) {
        // Update only if the row belongs to the authenticated user.
        FirebaseAuthService.VerifiedFirebaseUser user = verifyUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!isValidRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        return savedCalculationStore.update(user.uid(), id, request.toPayload())
                .map(saved -> ResponseEntity.ok(SavedCalculationResponse.from(saved)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/calculations/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable("id") long id
    ) {
        // Delete the saved calculation if it exists for this user.
        FirebaseAuthService.VerifiedFirebaseUser user = verifyUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean deleted = savedCalculationStore.delete(user.uid(), id);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    private FirebaseAuthService.VerifiedFirebaseUser verifyUser(String authorizationHeader) {
        // Parse Bearer token and verify via Firebase Admin SDK.
        String token = extractBearerToken(authorizationHeader);
        if (token == null) {
            logger.warn("Saved calculations rejected: missing or invalid Authorization header");
            return null;
        }

        try {
            return firebaseAuthService.verifyIdToken(token);
        } catch (FirebaseAuthException exception) {
            logger.warn("Saved calculations rejected: token verification error", exception);
            return null;
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }

        String prefix = "Bearer ";
        if (!authorizationHeader.startsWith(prefix) || authorizationHeader.length() <= prefix.length()) {
            return null;
        }

        return authorizationHeader.substring(prefix.length()).trim();
    }

    private String resolveUid(String authorizationHeader, UidCarrier request) {
        // Prefer verified Firebase uid when available, fall back to body uid for internal tools/tests.
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            FirebaseAuthService.VerifiedFirebaseUser user = verifyUser(authorizationHeader);
            if (user != null) {
                return user.uid();
            }
        }
        if (request == null || request.uid() == null || request.uid().isBlank()) {
            return null;
        }
        return request.uid().trim();
    }

    private boolean isValidRequest(SavedCalculationRequest request) {
        // Basic request validation to keep persistence layer clean.
        if (request == null || request.ticker() == null || request.ticker().isBlank()) {
            return false;
        }
        return Double.isFinite(request.initialInvestment())
                && Double.isFinite(request.years())
                && request.initialInvestment() > 0
                && request.years() > 0
                && Double.isFinite(request.beta())
                && Double.isFinite(request.expectedReturn())
                && Double.isFinite(request.futureValue());
    }

    public record SavedCalculationRequest(
            String uid,
            String name,
            String ticker,
            double initialInvestment,
            double years,
            double beta,
            double expectedReturn,
            double futureValue
    ) implements UidCarrier {
        public SavedCalculationStore.SavedCalculationPayload toPayload() {
            String normalizedTicker = ticker.trim().toUpperCase();
            String resolvedName = (name == null || name.isBlank()) ? normalizedTicker : name.trim();
            java.util.Map<String, Double> timeSeries =
                    com.els.backend.service.FutureValueService.computeTimeSeries(
                            initialInvestment, beta, expectedReturn, TIME_SERIES_YEARS);
            return new SavedCalculationStore.SavedCalculationPayload(
                    resolvedName,
                    normalizedTicker,
                    initialInvestment,
                    years,
                    beta,
                    expectedReturn,
                    futureValue,
                    timeSeries
            );
        }
    }

    public record SavedCalculationResponse(
            long id,
            String name,
            String ticker,
            double initialInvestment,
            double years,
            double beta,
            double expectedReturn,
            double futureValue,
            java.util.Map<String, Double> timeSeries,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {
        public static SavedCalculationResponse from(SavedCalculationStore.SavedCalculation saved) {
            return new SavedCalculationResponse(
                    saved.id(),
                    saved.name(),
                    saved.ticker(),
                    saved.initialInvestment(),
                    saved.years(),
                    saved.beta(),
                    saved.expectedReturn(),
                    saved.futureValue(),
                    saved.timeSeries(),
                    saved.createdAt(),
                    saved.updatedAt()
            );
        }
    }

    public record UidRequest(String uid) implements UidCarrier {
    }

    private sealed interface UidCarrier permits SavedCalculationRequest, UidRequest, SavedCalculationPatchRequest {
        String uid();
    }

    @GetMapping("/saved-calculations")
    public ResponseEntity<List<SavedCalculationResponse>> listSavedCalculations(
            @RequestBody(required = false) UidRequest request,
            @RequestParam(value = "name", required = false) String nameQuery
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        List<SavedCalculationResponse> responses = (
                nameQuery == null || nameQuery.isBlank()
                        ? savedCalculationStore.listByUid(uid)
                        : savedCalculationStore.listByUidAndNameLike(uid, nameQuery.trim())
        )
                .stream()
                .map(SavedCalculationResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/saved-calculations/{calculationId}")
    public ResponseEntity<Object> patchSavedCalculation(
            @PathVariable("calculationId") long calculationId,
            @RequestBody(required = false) SavedCalculationPatchRequest request
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Optional<SavedCalculationStore.SavedCalculation> existing =
                savedCalculationStore.getById(uid, calculationId);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        SavedCalculationStore.SavedCalculation current = existing.get();
        String updatedTicker = resolveTicker(request.ticker(), current.ticker());
        Double updatedInitialInvestment = resolveDouble(request.initialInvestment(), current.initialInvestment());
        Double updatedYears = resolveDouble(request.years(), current.years());
        String updatedName = resolveName(request.name(), current.name(), updatedTicker);

        if (updatedTicker == null || updatedTicker.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (updatedInitialInvestment == null || updatedInitialInvestment <= 0
                || updatedYears == null || updatedYears <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        boolean recompute = hasInputChange(current, updatedTicker, updatedInitialInvestment, updatedYears);
        double beta = current.beta();
        double expectedReturn = current.expectedReturn();
        double futureValue = current.futureValue();
        java.util.Map<String, Double> timeSeries = current.timeSeries();

        if (recompute) {
            com.els.backend.service.FutureValueService.FutureValueComputation computation =
                    com.els.backend.service.FutureValueService.computeFutureValue(
                            updatedTicker, updatedInitialInvestment, updatedYears);
            beta = computation.beta();
            expectedReturn = computation.expectedReturn();
            futureValue = computation.futureValue();
            timeSeries = com.els.backend.service.FutureValueService.computeTimeSeries(
                    updatedInitialInvestment, beta, expectedReturn, TIME_SERIES_YEARS);
        }

        SavedCalculationStore.SavedCalculationPayload payload =
                new SavedCalculationStore.SavedCalculationPayload(
                        updatedName,
                        updatedTicker,
                        updatedInitialInvestment,
                        updatedYears,
                        beta,
                        expectedReturn,
                        futureValue,
                        timeSeries
                );

        return savedCalculationStore.update(uid, calculationId, payload)
                .<ResponseEntity<Object>>map(saved -> ResponseEntity.ok((Object) SavedCalculationResponse.from(saved)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/saved-calculations/{calculationId}")
    public ResponseEntity<Object> deleteSavedCalculation(
            @PathVariable("calculationId") long calculationId,
            @RequestBody(required = false) UidRequest request
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        boolean deleted = savedCalculationStore.delete(uid, calculationId);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Calculation does not exist for this user."));
        }
        return ResponseEntity.ok().build();
    }

    private String extractUid(UidCarrier request) {
        if (request == null || request.uid() == null || request.uid().isBlank()) {
            return null;
        }
        return request.uid().trim();
    }

    private String resolveTicker(String requestTicker, String currentTicker) {
        if (requestTicker == null || requestTicker.isBlank()) {
            return currentTicker;
        }
        return requestTicker.trim().toUpperCase();
    }

    private Double resolveDouble(Double requested, double current) {
        return requested == null ? current : requested;
    }

    private String resolveName(String requestedName, String currentName, String updatedTicker) {
        if (requestedName == null) {
            return currentName;
        }
        if (requestedName.isBlank()) {
            return updatedTicker;
        }
        return requestedName.trim();
    }

    private boolean hasInputChange(SavedCalculationStore.SavedCalculation current,
                                   String ticker,
                                   double initialInvestment,
                                   double years) {
        return !current.ticker().equalsIgnoreCase(ticker)
                || Double.compare(current.initialInvestment(), initialInvestment) != 0
                || Double.compare(current.years(), years) != 0;
    }

    public record SavedCalculationPatchRequest(
            String uid,
            String name,
            String ticker,
            Double initialInvestment,
            Double years
    ) implements UidCarrier {
    }

    public record ErrorResponse(String message) {
    }
}
