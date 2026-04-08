package com.els.backend.controller;

import com.els.backend.database.calculations.SavedCalculationStore;
import com.els.backend.database.portfolios.PortfolioStore;
import com.els.backend.service.ExportWorkbookService;
import com.els.backend.service.FirebaseAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportControllerTest {

    private SavedCalculationStore savedCalculationStore;
    private PortfolioStore portfolioStore;
    private FirebaseAuthService firebaseAuthService;
    private ExportWorkbookService exportWorkbookService;
    private ExportController controller;

    @BeforeEach
    void setUp() {
        savedCalculationStore = mock(SavedCalculationStore.class);
        portfolioStore = mock(PortfolioStore.class);
        firebaseAuthService = mock(FirebaseAuthService.class);
        exportWorkbookService = mock(ExportWorkbookService.class);
        controller = new ExportController(savedCalculationStore, portfolioStore, firebaseAuthService, exportWorkbookService);
    }

    @Test
    void export_missingUid_returnsUnauthorized() {
        ExportController.ExportExcelRequest request =
                new ExportController.ExportExcelRequest(null, "calculations", null, null);
        ResponseEntity<?> response = controller.exportExcel(null, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void export_invalidScope_returnsBadRequest() {
        String uid = "uid-" + UUID.randomUUID();
        ExportController.ExportExcelRequest request =
                new ExportController.ExportExcelRequest(uid, "invalid", null, null);
        ResponseEntity<?> response = controller.exportExcel(null, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void export_calculations_returnsExcelFile() {
        String uid = "uid-" + UUID.randomUUID();
        SavedCalculationStore.SavedCalculation calculation = new SavedCalculationStore.SavedCalculation(
                7L,
                uid,
                "Retirement",
                "VFIAX",
                10000,
                10,
                1.2,
                0.08,
                21500,
                Map.of("0", 10000.0, "10", 21500.0),
                Instant.now(),
                Instant.now()
        );
        when(savedCalculationStore.listByUid(uid)).thenReturn(List.of(calculation));
        byte[] workbookBytes = new byte[]{1, 2, 3};
        when(exportWorkbookService.buildCalculationsWorkbook(anyList())).thenReturn(workbookBytes);

        ExportController.ExportExcelRequest request =
                new ExportController.ExportExcelRequest(uid, "calculations", null, null);
        ResponseEntity<?> response = controller.exportExcel(null, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().getContentType());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                response.getHeaders().getContentType().toString());
        assertNotNull(response.getHeaders().getFirst("Content-Disposition"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains(".xlsx"));
        assertArrayEquals(workbookBytes, (byte[]) response.getBody());
    }

    @Test
    void export_portfolio_withoutName_returnsBadRequest() {
        String uid = "uid-" + UUID.randomUUID();
        when(savedCalculationStore.listByUid(uid)).thenReturn(List.of());
        ExportController.ExportExcelRequest request =
                new ExportController.ExportExcelRequest(uid, "portfolio", null, null);

        ResponseEntity<?> response = controller.exportExcel(null, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void export_portfolio_notFound_returnsNotFound() {
        String uid = "uid-" + UUID.randomUUID();
        when(savedCalculationStore.listByUid(uid)).thenReturn(List.of());
        when(portfolioStore.getByName(uid, "Missing")).thenReturn(Optional.empty());

        ExportController.ExportExcelRequest request =
                new ExportController.ExportExcelRequest(uid, "portfolio", null, "Missing");

        ResponseEntity<?> response = controller.exportExcel(null, request);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void export_withBearerToken_usesVerifiedUid() throws Exception {
        String uid = "uid-" + UUID.randomUUID();
        FirebaseAuthService.VerifiedFirebaseUser user =
                new FirebaseAuthService.VerifiedFirebaseUser(uid, uid + "@mail.com", "User", null, "google");
        when(firebaseAuthService.verifyIdToken(anyString())).thenReturn(user);
        when(savedCalculationStore.listByUid(uid)).thenReturn(List.of());
        byte[] workbookBytes = new byte[]{5, 6, 7};
        when(exportWorkbookService.buildCalculationsWorkbook(anyList())).thenReturn(workbookBytes);

        ExportController.ExportExcelRequest request =
                new ExportController.ExportExcelRequest(null, "calculations", null, null);
        ResponseEntity<?> response = controller.exportExcel("Bearer token", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(workbookBytes, (byte[]) response.getBody());
    }

    @Test
    void export_portfolio_returnsExcelFile() {
        String uid = "uid-" + UUID.randomUUID();
        PortfolioStore.Portfolio portfolio = new PortfolioStore.Portfolio(
                10L, uid, "Growth", "Long term", Instant.now(), Instant.now());
        PortfolioStore.LinkedCalculation linked = new PortfolioStore.LinkedCalculation(
                3L, "VFIAX", 10000, 10, 1.2, 0.08, 21500, Instant.now(), Instant.now());

        when(portfolioStore.getByName(uid, "Growth")).thenReturn(Optional.of(portfolio));
        when(portfolioStore.listLinkedCalculations(uid, 10L)).thenReturn(List.of(linked));
        byte[] workbookBytes = new byte[]{9, 9, 9};
        when(exportWorkbookService.buildPortfolioWorkbook(any(), anyList())).thenReturn(workbookBytes);

        ExportController.ExportExcelRequest request =
                new ExportController.ExportExcelRequest(uid, "portfolio", null, "Growth");
        ResponseEntity<?> response = controller.exportExcel(null, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(workbookBytes, (byte[]) response.getBody());
    }

    @Test
    void export_all_returnsExcelFile() {
        String uid = "uid-" + UUID.randomUUID();
        SavedCalculationStore.SavedCalculation calculation = new SavedCalculationStore.SavedCalculation(
                1L,
                uid,
                "All Calc",
                "SWPPX",
                5000,
                5,
                1.0,
                0.05,
                6400,
                Map.of("0", 5000.0, "5", 6400.0),
                Instant.now(),
                Instant.now()
        );
        PortfolioStore.PortfolioSummary summary = new PortfolioStore.PortfolioSummary(
                10L, uid, "Growth", "Long term", Instant.now(), Instant.now(), 1, 5000, 6400, 1.0);
        PortfolioStore.LinkedCalculation linked = new PortfolioStore.LinkedCalculation(
                1L, "SWPPX", 5000, 5, 1.0, 0.05, 6400, Instant.now(), Instant.now());

        when(savedCalculationStore.listByUid(uid)).thenReturn(List.of(calculation));
        when(portfolioStore.listAllSummaries(uid)).thenReturn(List.of(summary));
        when(portfolioStore.listLinkedCalculations(uid, 10L)).thenReturn(List.of(linked));
        byte[] workbookBytes = new byte[]{8, 8, 8};
        when(exportWorkbookService.buildAllWorkbook(anyList(), anyList())).thenReturn(workbookBytes);

        ExportController.ExportExcelRequest request =
                new ExportController.ExportExcelRequest(uid, "all", null, null);
        ResponseEntity<?> response = controller.exportExcel(null, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(workbookBytes, (byte[]) response.getBody());
    }
}
