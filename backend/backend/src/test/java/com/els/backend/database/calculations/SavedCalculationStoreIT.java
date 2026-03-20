package com.els.backend.database.calculations;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Integration test hits the configured database; guarded by RUN_INTEGRATION=true.
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
@EnabledIfSystemProperty(named = "RUN_INTEGRATION", matches = "true")
class SavedCalculationStoreIT {

    @Autowired
    private SavedCalculationStore savedCalculationStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String testUid;

    @AfterEach
    void tearDown() {
        if (testUid == null) {
            return;
        }
        jdbcTemplate.update("delete from saved_calculations where uid = ?", testUid);
        jdbcTemplate.update("delete from users where uid = ?", testUid);
    }

    @Test
    void insert_list_update_delete_roundTrip() {
        testUid = "test-saved-calculation-" + UUID.randomUUID();
        ensureUserExists(testUid);

        SavedCalculationStore.SavedCalculationPayload payload =
                new SavedCalculationStore.SavedCalculationPayload(
                        "VFIAX",
                        10000,
                        10,
                        1.2,
                        0.08,
                        21500
                );

        SavedCalculationStore.SavedCalculation inserted = savedCalculationStore.insert(testUid, payload);
        assertNotNull(inserted);
        assertEquals(testUid, inserted.uid());
        assertEquals("VFIAX", inserted.ticker());

        List<SavedCalculationStore.SavedCalculation> list = savedCalculationStore.listByUid(testUid);
        assertEquals(1, list.size());
        assertEquals(inserted.id(), list.get(0).id());

        SavedCalculationStore.SavedCalculationPayload updatePayload =
                new SavedCalculationStore.SavedCalculationPayload(
                        "FDGRX",
                        12000,
                        12,
                        1.1,
                        0.07,
                        24800
                );

        Optional<SavedCalculationStore.SavedCalculation> updated =
                savedCalculationStore.update(testUid, inserted.id(), updatePayload);
        assertTrue(updated.isPresent());
        assertEquals("FDGRX", updated.get().ticker());

        boolean deleted = savedCalculationStore.delete(testUid, inserted.id());
        assertTrue(deleted);

        List<SavedCalculationStore.SavedCalculation> afterDelete = savedCalculationStore.listByUid(testUid);
        assertTrue(afterDelete.isEmpty());
    }

    @Test
    void update_missingRow_returnsEmptyOptional() {
        testUid = "test-saved-calculation-" + UUID.randomUUID();
        ensureUserExists(testUid);

        SavedCalculationStore.SavedCalculationPayload payload =
                new SavedCalculationStore.SavedCalculationPayload(
                        "SWPPX",
                        5000,
                        5,
                        1.0,
                        0.05,
                        6400
                );

        Optional<SavedCalculationStore.SavedCalculation> updated =
                savedCalculationStore.update(testUid, 999999L, payload);
        assertTrue(updated.isEmpty());
    }

    @Test
    void delete_missingRow_returnsFalse() {
        testUid = "test-saved-calculation-" + UUID.randomUUID();
        ensureUserExists(testUid);

        boolean deleted = savedCalculationStore.delete(testUid, 999999L);
        assertFalse(deleted);
    }

    private void ensureUserExists(String uid) {
        jdbcTemplate.update("""
                insert into users (uid, email, display_name, photo_url, auth_provider, created_at, updated_at)
                values (?, ?, ?, ?, ?, now(), now())
                on conflict (uid) do nothing
                """, uid, uid + "@example.com", "Test User", null, "password");
    }
}
