package com.els.backend.controller;

import com.els.backend.database.auth.AuthStore;
import com.els.backend.service.FirebaseAuthResponse;
import com.els.backend.service.FirebaseAuthService;
import com.google.firebase.auth.FirebaseAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "${app.frontend-origin}")
public class AuthSyncController {

    private static final Logger logger = LoggerFactory.getLogger(AuthSyncController.class);
    private final FirebaseAuthService firebaseAuthService;
    private final AuthStore authStore;

    public AuthSyncController(FirebaseAuthService firebaseAuthService, AuthStore authStore) {
        this.firebaseAuthService = firebaseAuthService;
        this.authStore = authStore;
    }

    @PostMapping("/sync")
    public ResponseEntity<FirebaseAuthResponse> syncUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) AuthSyncRequest request
    ) {
        String idToken = extractBearerToken(authorizationHeader);
        if (idToken == null) {
            logger.warn("Auth sync rejected: missing or invalid Authorization header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    FirebaseAuthResponse.error("Missing or invalid Authorization header.")
            );
        }

        try {
            FirebaseAuthService.VerifiedFirebaseUser verifiedUser = firebaseAuthService.verifyIdToken(idToken);

            // Optional consistency check if caller also sends uid in the body.
            if (request != null && request.uid() != null && !request.uid().isBlank()
                    && !verifiedUser.uid().equals(request.uid().trim())) {
                logger.warn("Auth sync rejected: request uid does not match verified uid");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        FirebaseAuthResponse.error("Request uid does not match verified Firebase uid.")
                );
            }

            FirebaseAuthResponse response = new FirebaseAuthResponse();
            response.setSuccess(true);
            response.setUid(verifiedUser.uid());
            response.setEmail(verifiedUser.email());
            response.setDisplayName(verifiedUser.displayName());
            response.setPhotoUrl(verifiedUser.photoUrl());
            response.setAuthProvider(verifiedUser.authProvider());
            boolean created = authStore.insertIfMissing(verifiedUser);
            response.setSyncStatus(created ? "created" : "existing");
            response.setMessage(created
                    ? "Verified Firebase user created in PostgreSQL."
                    : "Verified Firebase user already exists in PostgreSQL.");

            logger.info("Auth sync verified user uid={} provider={}", verifiedUser.uid(), verifiedUser.authProvider());
            return ResponseEntity.ok(response);
        } catch (FirebaseAuthException exception) {
            logger.warn("Auth sync failed: token verification error", exception);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    FirebaseAuthResponse.error("Firebase token verification failed: " + exception.getMessage())
            );
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

    public record AuthSyncRequest(String uid) {
    }
}
