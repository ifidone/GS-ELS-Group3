package com.els.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class FirebaseAuthResponseTest {

    @Test
    void errorFactory_setsFailureAndMessage() {
        FirebaseAuthResponse response = FirebaseAuthResponse.error("boom");

        assertFalse(response.isSuccess());
        assertEquals("boom", response.getError());
        assertNull(response.getUid());
    }

    @Test
    void settersRoundTripValues() {
        FirebaseAuthResponse response = new FirebaseAuthResponse();
        response.setSuccess(true);
        response.setUid("uid-1");
        response.setEmail("user@example.com");
        response.setDisplayName("User");
        response.setPhotoUrl("https://photo");
        response.setAuthProvider("google.com");
        response.setSyncStatus("created");
        response.setMessage("ok");
        response.setError(null);

        assertEquals(true, response.isSuccess());
        assertEquals("uid-1", response.getUid());
        assertEquals("user@example.com", response.getEmail());
        assertEquals("User", response.getDisplayName());
        assertEquals("https://photo", response.getPhotoUrl());
        assertEquals("google.com", response.getAuthProvider());
        assertEquals("created", response.getSyncStatus());
        assertEquals("ok", response.getMessage());
        assertNull(response.getError());
    }
}
