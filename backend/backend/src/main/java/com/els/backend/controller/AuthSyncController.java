package com.els.backend.controller;

import com.els.backend.service.FirebaseAuthService;
import com.google.firebase.auth.FirebaseAuthException;
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

    private final FirebaseAuthService firebaseAuthService;

    public AuthSyncController(FirebaseAuthService firebaseAuthService) {
        this.firebaseAuthService = firebaseAuthService;
    }

    @PostMapping("/sync")
    public ResponseEntity<AuthSyncResponse> syncUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) AuthSyncRequest request
    ) {
        String idToken = extractBearerToken(authorizationHeader);
        if (idToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    AuthSyncResponse.error("Missing or invalid Authorization header.")
            );
        }

        try {
            FirebaseAuthService.VerifiedFirebaseUser verifiedUser = firebaseAuthService.verifyIdToken(idToken);

            if (request != null && request.uid() != null && !request.uid().isBlank()
                    && !verifiedUser.uid().equals(request.uid().trim())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        AuthSyncResponse.error("Request uid does not match verified Firebase uid.")
                );
            }

            return ResponseEntity.ok(new AuthSyncResponse(
                    true,
                    verifiedUser.uid(),
                    verifiedUser.email(),
                    verifiedUser.displayName(),
                    verifiedUser.photoUrl(),
                    verifiedUser.authProvider(),
                    "pending",
                    "Verified Firebase user accepted. PostgreSQL sync is not wired yet.",
                    null
            ));
        } catch (FirebaseAuthException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    AuthSyncResponse.error("Firebase token verification failed: " + exception.getMessage())
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

    public record AuthSyncResponse(
            boolean success,
            String uid,
            String email,
            String displayName,
            String photoUrl,
            String authProvider,
            String syncStatus,
            String message,
            String error
    ) {
        public static AuthSyncResponse error(String errorMessage) {
            return new AuthSyncResponse(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    errorMessage
            );
        }
    }
}
