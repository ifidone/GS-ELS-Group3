package com.els.backend.database.calculations;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SavedCalculationStoreTest {

    @Test
    void listByUid_returnsRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SavedCalculationStore store = new SavedCalculationStore(jdbcTemplate);
        SavedCalculationStore.SavedCalculation row =
                new SavedCalculationStore.SavedCalculation(
                        1L,
                        "uid-1",
                        "VFIAX",
                        "VFIAX",
                        10000,
                        10,
                        1.2,
                        0.08,
                        21500,
                        Map.of("0", 10000.0, "10", 21500.0),
                        java.time.Instant.now(),
                        java.time.Instant.now()
                );
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(List.of(row));

        List<SavedCalculationStore.SavedCalculation> results = store.listByUid("uid-1");
        assertEquals(1, results.size());
    }

    @Test
    void insert_returnsRow() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SavedCalculationStore store = new SavedCalculationStore(jdbcTemplate);
        SavedCalculationStore.SavedCalculation row =
                new SavedCalculationStore.SavedCalculation(
                        1L,
                        "uid-1",
                        "VFIAX",
                        "VFIAX",
                        10000,
                        10,
                        1.2,
                        0.08,
                        21500,
                        Map.of("0", 10000.0, "10", 21500.0),
                        java.time.Instant.now(),
                        java.time.Instant.now()
                );
        when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(row);

        SavedCalculationStore.SavedCalculation inserted = store.insert(
                "uid-1",
                new SavedCalculationStore.SavedCalculationPayload(
                        "VFIAX",
                        "VFIAX",
                        10000,
                        10,
                        1.2,
                        0.08,
                        21500,
                        Map.of("0", 10000.0, "10", 21500.0)
                )
        );
        assertNotNull(inserted);
    }

    @Test
    void update_missingRow_returnsEmptyOptional() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SavedCalculationStore store = new SavedCalculationStore(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        Optional<SavedCalculationStore.SavedCalculation> updated =
                store.update("uid-1", 999999L,
                        new SavedCalculationStore.SavedCalculationPayload(
                                "SWPPX",
                                "SWPPX",
                                5000,
                                5,
                                1.0,
                                0.05,
                                6400,
                                Map.of("0", 5000.0, "5", 6400.0)
                        ));
        assertTrue(updated.isEmpty());
    }

    @Test
    void delete_missingRow_returnsFalse() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SavedCalculationStore store = new SavedCalculationStore(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(0);

        boolean deleted = store.delete("uid-1", 999999L);
        assertFalse(deleted);
    }
}
