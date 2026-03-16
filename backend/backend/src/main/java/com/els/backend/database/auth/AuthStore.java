package com.els.backend.database.auth;

import com.els.backend.service.FirebaseAuthService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthStore {

    private final JdbcTemplate jdbcTemplate;

    public AuthStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Inserts a user record on first auth sync; leaves existing rows unchanged.
    public boolean insertIfMissing(FirebaseAuthService.VerifiedFirebaseUser user) {
        String sql = """
                insert into users (
                    uid,
                    email,
                    display_name,
                    photo_url,
                    auth_provider,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, now(), now())
                on conflict (uid) do nothing
                """;
        int updated = jdbcTemplate.update(
                sql,
                user.uid(),
                user.email(),
                user.displayName(),
                user.photoUrl(),
                user.authProvider()
        );
        return updated > 0;
    }
}
