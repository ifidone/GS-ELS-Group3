package com.els.backend.database.auth;

import com.els.backend.service.FirebaseAuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthStoreTest {

    @Test
    void insertIfMissing_whenRowInserted_returnsTrue() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any())).thenReturn(1);

        AuthStore store = new AuthStore(jdbcTemplate);
        FirebaseAuthService.VerifiedFirebaseUser user = new FirebaseAuthService.VerifiedFirebaseUser(
                "uid-1",
                "user@example.com",
                "User",
                "https://photo",
                "google.com"
        );

        assertTrue(store.insertIfMissing(user));
    }

    @Test
    void insertIfMissing_whenNoRowInserted_returnsFalse() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any())).thenReturn(0);

        AuthStore store = new AuthStore(jdbcTemplate);
        FirebaseAuthService.VerifiedFirebaseUser user = new FirebaseAuthService.VerifiedFirebaseUser(
                "uid-2",
                "user2@example.com",
                "User2",
                null,
                "google.com"
        );

        assertFalse(store.insertIfMissing(user));
    }
}
