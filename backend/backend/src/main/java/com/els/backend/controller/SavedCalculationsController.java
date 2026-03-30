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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/calculations")
@CrossOrigin(origins = "${app.frontend-origin}")
public class SavedCalculationsController {

    // CRUD endpoints for saved calculator runs tied to Firebase-authenticated users.
    private static final Logger logger = LoggerFactory.getLogger(SavedCalculationsController.class);
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

    @GetMapping
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

    @PostMapping
    public ResponseEntity<SavedCalculationResponse> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) SavedCalculationRequest request
    ) {
        // Validate input and persist a new saved calculation for the caller.
        FirebaseAuthService.VerifiedFirebaseUser user = verifyUser(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!isValidRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        authStore.insertIfMissing(user);
        SavedCalculationStore.SavedCalculation saved = savedCalculationStore.insert(
                user.uid(),
                request.toPayload()
        );
        return ResponseEntity.ok(SavedCalculationResponse.from(saved));
    }

    @PutMapping("/{id}")
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

    @DeleteMapping("/{id}")
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

    private String resolveUid(String authorizationHeader, UidRequest request) {
        // Prefer verified Firebase uid when available, fall back to body uid for internal tools/tests.
        FirebaseAuthService.VerifiedFirebaseUser user = verifyUser(authorizationHeader);
        if (user != null) {
            return user.uid();
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
            String ticker,
            double initialInvestment,
            double years,
            double beta,
            double expectedReturn,
            double futureValue
    ) {
        public SavedCalculationStore.SavedCalculationPayload toPayload() {
            return new SavedCalculationStore.SavedCalculationPayload(
                    ticker.trim().toUpperCase(),
                    initialInvestment,
                    years,
                    beta,
                    expectedReturn,
                    futureValue
            );
        }
    }

    public record SavedCalculationResponse(
            long id,
            String ticker,
            double initialInvestment,
            double years,
            double beta,
            double expectedReturn,
            double futureValue,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {
        public static SavedCalculationResponse from(SavedCalculationStore.SavedCalculation saved) {
            return new SavedCalculationResponse(
                    saved.id(),
                    saved.ticker(),
                    saved.initialInvestment(),
                    saved.years(),
                    saved.beta(),
                    saved.expectedReturn(),
                    saved.futureValue(),
                    saved.createdAt(),
                    saved.updatedAt()
            );
        }
    }

    public record UidRequest(String uid) {
    }
}
