package com.els.backend.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;

@Service
public class FirebaseAuthService {

    private final String firebaseCredentialsPath;
    private final String firebaseCredentialsJsonBase64;

    public FirebaseAuthService(@Value("${firebase.credentials.path:}") String firebaseCredentialsPath,
                               @Value("${firebase.credentials.json-base64:}") String firebaseCredentialsJsonBase64) {
        this.firebaseCredentialsPath = firebaseCredentialsPath;
        this.firebaseCredentialsJsonBase64 = firebaseCredentialsJsonBase64;
    }

    private synchronized void initializeFirebaseAppIfNeeded() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        if ((firebaseCredentialsJsonBase64 == null || firebaseCredentialsJsonBase64.isBlank())
                && (firebaseCredentialsPath == null || firebaseCredentialsPath.isBlank())) {
            throw new IllegalStateException(
                    "Firebase credentials are not configured. " +
                            "Set FIREBASE_CREDENTIALS_JSON_BASE64 or FIREBASE_CREDENTIALS."
            );
        }

        try {
            GoogleCredentials credentials = loadCredentials();
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            FirebaseApp.initializeApp(options);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to initialize Firebase Admin SDK using configured Firebase credentials.",
                    exception
            );
        }
    }

    private GoogleCredentials loadCredentials() throws IOException {
        if (firebaseCredentialsJsonBase64 != null && !firebaseCredentialsJsonBase64.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(firebaseCredentialsJsonBase64);
            return GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
        }

        return GoogleCredentials.fromStream(new FileInputStream(firebaseCredentialsPath));
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
