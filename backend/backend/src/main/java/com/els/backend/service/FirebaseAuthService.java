package com.els.backend.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;

@Service
public class FirebaseAuthService {

    private final String firebaseCredentialsPath;

    public FirebaseAuthService(@Value("${firebase.credentials.path:}") String firebaseCredentialsPath) {
        this.firebaseCredentialsPath = firebaseCredentialsPath;
    }

    private synchronized void initializeFirebaseAppIfNeeded() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        if (firebaseCredentialsPath == null || firebaseCredentialsPath.isBlank()) {
            throw new IllegalStateException(
                    "Firebase credentials are not configured. " +
                            "Set GOOGLE_APPLICATION_CREDENTIALS and run with the local Spring profile."
            );
        }

        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(new FileInputStream(firebaseCredentialsPath)))
                    .build();
            FirebaseApp.initializeApp(options);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to initialize Firebase Admin SDK using firebase.credentials.path=" + firebaseCredentialsPath,
                    exception
            );
        }
    }

    public VerifiedFirebaseUser verifyIdToken(String idToken) throws FirebaseAuthException {
        initializeFirebaseAppIfNeeded();
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);

        String provider = null;
        Object firebaseClaim = decodedToken.getClaims().get("firebase");
        if (firebaseClaim instanceof java.util.Map<?, ?> firebaseMap) {
            Object signInProvider = firebaseMap.get("sign_in_provider");
            if (signInProvider instanceof String signInProviderString) {
                provider = signInProviderString;
            }
        }

        return new VerifiedFirebaseUser(
                decodedToken.getUid(),
                decodedToken.getEmail(),
                (String) decodedToken.getClaims().get("name"),
                (String) decodedToken.getClaims().get("picture"),
                provider
        );
    }

    public record VerifiedFirebaseUser(
            String uid,
            String email,
            String displayName,
            String photoUrl,
            String authProvider
    ) {
    }
}
