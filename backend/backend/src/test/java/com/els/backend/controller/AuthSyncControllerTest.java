package com.els.backend.controller;

import com.els.backend.database.auth.AuthStore;
import com.els.backend.service.FirebaseAuthService;
import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSyncControllerTest {

    private MockMvc mockMvc;

    private FirebaseAuthService firebaseAuthService;

    private AuthStore authStore;

    @BeforeEach
    void setUp() {
        firebaseAuthService = Mockito.mock(FirebaseAuthService.class);
        authStore = Mockito.mock(AuthStore.class);
        AuthSyncController controller = new AuthSyncController(firebaseAuthService, authStore);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addPlaceholderValue("app.frontend-origin", "http://localhost")
                .build();
    }

    @Test
    void syncUser_missingAuthorization_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void syncUser_uidMismatch_returnsUnauthorized() throws Exception {
        FirebaseAuthService.VerifiedFirebaseUser verifiedUser =
                new FirebaseAuthService.VerifiedFirebaseUser("verified", "user@example.com", null, null, "google.com");
        when(firebaseAuthService.verifyIdToken(anyString())).thenReturn(verifiedUser);

        mockMvc.perform(post("/api/auth/sync")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"different\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void syncUser_success_returnsOk() throws Exception {
        FirebaseAuthService.VerifiedFirebaseUser verifiedUser =
                new FirebaseAuthService.VerifiedFirebaseUser("uid-1", "user@example.com", "User", null, "google.com");
        when(firebaseAuthService.verifyIdToken(anyString())).thenReturn(verifiedUser);
        when(authStore.insertIfMissing(Mockito.any())).thenReturn(true);

        mockMvc.perform(post("/api/auth/sync")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"uid-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.uid").value("uid-1"))
                .andExpect(jsonPath("$.syncStatus").value("created"));
    }

    @Test
    void syncUser_firebaseError_returnsUnauthorized() throws Exception {
        when(firebaseAuthService.verifyIdToken(anyString()))
                .thenThrow(Mockito.mock(FirebaseAuthException.class));

        mockMvc.perform(post("/api/auth/sync")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"uid-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }
}
