package com.els.backend.controller;

import com.els.backend.database.auth.AuthStore;
import com.els.backend.database.portfolios.PortfolioStore;
import com.els.backend.service.FirebaseAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioControllerTest {

    private PortfolioStore portfolioStore;
    private AuthStore authStore;
    private FirebaseAuthService firebaseAuthService;
    private PortfolioController controller;

    @BeforeEach
    void setUp() {
        portfolioStore = mock(PortfolioStore.class);
        authStore = mock(AuthStore.class);
        firebaseAuthService = mock(FirebaseAuthService.class);
        controller = new PortfolioController(portfolioStore, authStore, firebaseAuthService);
    }

    @Test
    void list_missingUid_returnsUnauthorized() {
        ResponseEntity<List<PortfolioController.PortfolioListItemResponse>> response =
                controller.list(null, null, 0, 20, "createdAt,desc");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void create_validRequest_returnsCreated() {
        String uid = "uid-" + UUID.randomUUID();
        PortfolioStore.Portfolio portfolio = new PortfolioStore.Portfolio(
                1L,
                uid,
                "Retirement 2045",
                "Long-term portfolio",
                Instant.now(),
                Instant.now()
        );
        when(portfolioStore.create(anyString(), anyString(), any())).thenReturn(portfolio);

        PortfolioController.PortfolioCreateRequest request =
                new PortfolioController.PortfolioCreateRequest(uid, "Retirement 2045", "Long-term portfolio");
        ResponseEntity<Object> response = controller.create(null, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        PortfolioController.PortfolioMetadata body = (PortfolioController.PortfolioMetadata) response.getBody();
        assertEquals("Retirement 2045", body.name());
    }

    @Test
    void get_missingPortfolio_returnsNotFound() {
        String uid = "uid-" + UUID.randomUUID();
        when(portfolioStore.getByName(uid, "Missing Portfolio")).thenReturn(Optional.empty());

        PortfolioController.PortfolioNameRequest request =
                new PortfolioController.PortfolioNameRequest(uid, "Missing Portfolio");
        ResponseEntity<PortfolioController.PortfolioDetailResponse> response =
                controller.get(null, "Missing Portfolio", request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listItems_missingPortfolio_returnsNotFound() {
        String uid = "uid-" + UUID.randomUUID();
        when(portfolioStore.getIdByName(uid, "Retirement 2045")).thenReturn(Optional.empty());

        PortfolioController.UidRequest request = new PortfolioController.UidRequest(uid);
        ResponseEntity<List<PortfolioController.LinkedCalculationResponse>> response =
                controller.listItems(null, "Retirement 2045", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void addItem_missingCalculation_returnsNotFound() {
        String uid = "uid-" + UUID.randomUUID();
        when(portfolioStore.getIdByName(uid, "Aggressive Growth")).thenReturn(Optional.of(10L));
        when(portfolioStore.calculationExistsForUid(uid, 55L)).thenReturn(false);

        PortfolioController.UidRequest request = new PortfolioController.UidRequest(uid);
        ResponseEntity<Void> response = controller.addItem(null, "Aggressive Growth", 55L, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void update_byName_returnsConflictOnDuplicate() {
        String uid = "uid-" + UUID.randomUUID();
        PortfolioStore.Portfolio existing = new PortfolioStore.Portfolio(
                5L,
                uid,
                "Retirement 2045",
                "Long-term",
                Instant.now(),
                Instant.now()
        );
        when(portfolioStore.getByName(uid, "Retirement 2045")).thenReturn(Optional.of(existing));
        when(portfolioStore.portfolioNameExistsExcludingId(uid, "Aggressive Growth", 5L)).thenReturn(true);

        PortfolioController.PortfolioUpdateRequest request =
                new PortfolioController.PortfolioUpdateRequest(uid, "Aggressive Growth", "Updated");
        ResponseEntity<Object> response = controller.update(null, "Retirement 2045", request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void delete_byName_missing_returnsNotFound() {
        String uid = "uid-" + UUID.randomUUID();
        when(portfolioStore.deleteByName(uid, "Aggressive Growth")).thenReturn(false);

        PortfolioController.UidRequest request = new PortfolioController.UidRequest(uid);
        ResponseEntity<Void> response = controller.delete(null, "Aggressive Growth", request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void availableCalculations_missingPortfolio_returnsNotFound() {
        String uid = "uid-" + UUID.randomUUID();
        when(portfolioStore.getIdByName(uid, "Aggressive Growth")).thenReturn(Optional.empty());

        PortfolioController.UidRequest request = new PortfolioController.UidRequest(uid);
        ResponseEntity<List<PortfolioController.LinkedCalculationResponse>> response =
                controller.listAvailable(null, "Aggressive Growth", request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void availableCalculations_returnsRows() {
        String uid = "uid-" + UUID.randomUUID();
        when(portfolioStore.getIdByName(uid, "Aggressive Growth")).thenReturn(Optional.of(11L));
        PortfolioStore.LinkedCalculation available = new PortfolioStore.LinkedCalculation(
                8L,
                "SWPPX",
                5000,
                5,
                1.0,
                0.05,
                6400,
                Instant.now(),
                Instant.now()
        );
        when(portfolioStore.listAvailableCalculations(uid, 11L)).thenReturn(List.of(available));

        PortfolioController.UidRequest request = new PortfolioController.UidRequest(uid);
        ResponseEntity<List<PortfolioController.LinkedCalculationResponse>> response =
                controller.listAvailable(null, "Aggressive Growth", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void listItems_returnsLinkedCalculations() {
        String uid = "uid-" + UUID.randomUUID();
        when(portfolioStore.getIdByName(uid, "Aggressive Growth")).thenReturn(Optional.of(10L));
        PortfolioStore.LinkedCalculation linked = new PortfolioStore.LinkedCalculation(
                3L,
                "VFIAX",
                10000,
                10,
                1.2,
                0.08,
                21500,
                Instant.now(),
                Instant.now()
        );
        when(portfolioStore.listLinkedCalculations(uid, 10L)).thenReturn(List.of(linked));

        PortfolioController.UidRequest request = new PortfolioController.UidRequest(uid);
        ResponseEntity<List<PortfolioController.LinkedCalculationResponse>> response =
                controller.listItems(null, "Aggressive Growth", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void addItem_success_returnsOk() {
        String uid = "uid-" + UUID.randomUUID();
        when(portfolioStore.getIdByName(uid, "Aggressive Growth")).thenReturn(Optional.of(10L));
        when(portfolioStore.calculationExistsForUid(uid, 3L)).thenReturn(true);

        PortfolioController.UidRequest request = new PortfolioController.UidRequest(uid);
        ResponseEntity<Void> response = controller.addItem(null, "Aggressive Growth", 3L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void removeItem_notLinked_returnsNotFound() {
        String uid = "uid-" + UUID.randomUUID();
        when(portfolioStore.getIdByName(uid, "Aggressive Growth")).thenReturn(Optional.of(10L));
        when(portfolioStore.calculationExistsForUid(uid, 3L)).thenReturn(true);
        when(portfolioStore.removeCalculation(uid, 10L, 3L)).thenReturn(false);

        PortfolioController.UidRequest request = new PortfolioController.UidRequest(uid);
        ResponseEntity<Object> response = controller.removeItem(null, "Aggressive Growth", 3L, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void removeItem_success_returnsOk() {
        String uid = "uid-" + UUID.randomUUID();
        when(portfolioStore.getIdByName(uid, "Aggressive Growth")).thenReturn(Optional.of(10L));
        when(portfolioStore.calculationExistsForUid(uid, 3L)).thenReturn(true);
        when(portfolioStore.removeCalculation(uid, 10L, 3L)).thenReturn(true);

        PortfolioController.UidRequest request = new PortfolioController.UidRequest(uid);
        ResponseEntity<Object> response = controller.removeItem(null, "Aggressive Growth", 3L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void list_returnsSummaries() {
        String uid = "uid-" + UUID.randomUUID();
        PortfolioStore.PortfolioSummary summary = new PortfolioStore.PortfolioSummary(
                7L,
                uid,
                "Aggressive Growth",
                "High-risk bucket",
                Instant.now(),
                Instant.now(),
                0,
                0,
                0,
                0
        );
        when(portfolioStore.listSummaries(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(List.of(summary));
        when(portfolioStore.listLinkedCalculations(anyString(), anyLong()))
                .thenReturn(List.of());

        PortfolioController.UidRequest request = new PortfolioController.UidRequest(uid);
        ResponseEntity<List<PortfolioController.PortfolioListItemResponse>> response =
                controller.list(null, request, 0, 20, "createdAt,desc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }
}
