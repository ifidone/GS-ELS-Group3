package com.els.backend.database.portfolios;

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

@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
@EnabledIfSystemProperty(named = "RUN_INTEGRATION", matches = "true")
class PortfolioStoreIT {

    @Autowired
    private PortfolioStore portfolioStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String testUid;
    private Long portfolioId;
    private Long calculationId;

    @AfterEach
    void tearDown() {
        if (portfolioId != null) {
            jdbcTemplate.update("delete from portfolio_items where portfolio_id = ?", portfolioId);
            jdbcTemplate.update("delete from portfolios where id = ?", portfolioId);
        }
        if (calculationId != null) {
            jdbcTemplate.update("delete from saved_calculations where id = ?", calculationId);
        }
        if (testUid != null) {
            jdbcTemplate.update("delete from users where uid = ?", testUid);
        }
    }

    @Test
    void create_list_update_delete_roundTrip() {
        testUid = "test-portfolio-" + UUID.randomUUID();
        ensureUserExists(testUid);

        PortfolioStore.Portfolio created = portfolioStore.create(
                testUid,
                "Retirement 2045",
                "Long-term plan"
        );
        assertNotNull(created);
        portfolioId = created.id();

        List<PortfolioStore.PortfolioSummary> summaries =
                portfolioStore.listSummaries(testUid, 20, 0, "p.created_at desc");
        assertFalse(summaries.isEmpty());

        Optional<PortfolioStore.Portfolio> fetched = portfolioStore.getById(testUid, portfolioId);
        assertTrue(fetched.isPresent());

        Optional<PortfolioStore.Portfolio> updated =
                portfolioStore.update(testUid, portfolioId, "Retirement 2045 Updated", "Updated");
        assertTrue(updated.isPresent());

        boolean deleted = portfolioStore.delete(testUid, portfolioId);
        assertTrue(deleted);
        portfolioId = null;
    }

    @Test
    void add_and_remove_calculation() {
        testUid = "test-portfolio-" + UUID.randomUUID();
        ensureUserExists(testUid);
        calculationId = insertSavedCalculation(testUid);

        PortfolioStore.Portfolio created = portfolioStore.create(
                testUid,
                "Aggressive Growth",
                "High-risk"
        );
        portfolioId = created.id();

        boolean added = portfolioStore.addCalculation(testUid, portfolioId, calculationId);
        assertTrue(added);

        List<PortfolioStore.LinkedCalculation> linked =
                portfolioStore.listLinkedCalculations(testUid, portfolioId);
        assertEquals(1, linked.size());

        List<PortfolioStore.LinkedCalculation> available =
                portfolioStore.listAvailableCalculations(testUid, portfolioId);
        assertTrue(available.isEmpty());

        boolean removed = portfolioStore.removeCalculation(testUid, portfolioId, calculationId);
        assertTrue(removed);
    }

    private void ensureUserExists(String uid) {
        jdbcTemplate.update("""
                insert into users (uid, email, display_name, photo_url, auth_provider, created_at, updated_at)
                values (?, ?, ?, ?, ?, now(), now())
                on conflict (uid) do nothing
                """, uid, uid + "@example.com", "Test User", null, "password");
    }

    private long insertSavedCalculation(String uid) {
        return jdbcTemplate.queryForObject("""
                insert into saved_calculations (
                    uid,
                    name,
                    ticker,
                    initial_investment,
                    years,
                    beta,
                    expected_return,
                    future_value,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                returning id
                """, Long.class, uid, "VFIAX", "VFIAX", 10000, 10, 1.2, 0.08, 21500);
    }
}
