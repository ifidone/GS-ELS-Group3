package com.els.backend.service;

import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FirebaseAuthServiceTest {

    @Test
    void verifyIdToken_withoutCredentials_throwsIllegalState() {
        FirebaseAuthService service = new FirebaseAuthService("", "");

        assertThrows(IllegalStateException.class, () -> {
            try {
                service.verifyIdToken("test-token");
            } catch (FirebaseAuthException exception) {
                throw new RuntimeException(exception);
            }
        });
    }
}
