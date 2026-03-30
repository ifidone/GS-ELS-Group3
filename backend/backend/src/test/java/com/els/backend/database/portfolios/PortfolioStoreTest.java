package com.els.backend.database.portfolios;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioStoreTest {

    @Test
    void listSummaries_returnsRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PortfolioStore store = new PortfolioStore(jdbcTemplate);
        PortfolioStore.PortfolioSummary summary = new PortfolioStore.PortfolioSummary(
                1L,
                "uid-1",
                "Retirement 2045",
                "Long-term",
                Instant.now(),
                Instant.now(),
                2,
                50000,
                80000,
                0.9
        );
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), anyInt(), anyInt()))
                .thenReturn(List.of(summary));

        List<PortfolioStore.PortfolioSummary> results = store.listSummaries("uid-1", 20, 0, "p.created_at desc");
        assertEquals(1, results.size());
    }

    @Test
    void create_returnsRow() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PortfolioStore store = new PortfolioStore(jdbcTemplate);
        PortfolioStore.Portfolio portfolio = new PortfolioStore.Portfolio(
                1L,
                "uid-1",
                "Aggressive Growth",
                "High-risk",
                Instant.now(),
                Instant.now()
        );
        when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any()))
                .thenReturn(portfolio);

        PortfolioStore.Portfolio created = store.create("uid-1", "Aggressive Growth", "High-risk");
        assertNotNull(created);
    }

    @Test
    void update_missingRow_returnsEmptyOptional() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PortfolioStore store = new PortfolioStore(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any(), anyLong()))
                .thenReturn(List.of());

        Optional<PortfolioStore.Portfolio> updated = store.update("uid-1", 999L, "Name", "Desc");
        assertTrue(updated.isEmpty());
    }
}
