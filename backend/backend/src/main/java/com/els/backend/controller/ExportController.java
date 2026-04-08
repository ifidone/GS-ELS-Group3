package com.els.backend.controller;

import com.els.backend.database.calculations.SavedCalculationStore;
import com.els.backend.database.portfolios.PortfolioStore;
import com.els.backend.service.ExportWorkbookService;
import com.els.backend.service.FirebaseAuthService;
import com.google.firebase.auth.FirebaseAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/exports")
@CrossOrigin(origins = "${app.frontend-origin}")
public class ExportController {

    private static final Logger logger = LoggerFactory.getLogger(ExportController.class);
    private static final MediaType EXCEL_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SavedCalculationStore savedCalculationStore;
    private final PortfolioStore portfolioStore;
    private final FirebaseAuthService firebaseAuthService;
    private final ExportWorkbookService exportWorkbookService;

    public ExportController(SavedCalculationStore savedCalculationStore,
                            PortfolioStore portfolioStore,
                            FirebaseAuthService firebaseAuthService,
                            ExportWorkbookService exportWorkbookService) {
        this.savedCalculationStore = savedCalculationStore;
        this.portfolioStore = portfolioStore;
        this.firebaseAuthService = firebaseAuthService;
        this.exportWorkbookService = exportWorkbookService;
    }

    @PostMapping("/excel")
    public ResponseEntity<?> exportExcel(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) ExportExcelRequest request
    ) {
        // One export endpoint, scope-driven so frontend can reuse it across pages.
        if (request == null || request.scope() == null || request.scope().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Request must include scope."));
        }

        ExportScope scope = ExportScope.from(request.scope());
        if (scope == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Invalid scope. Use calculations, portfolio, or all."));
        }

        String uid = resolveUid(authorizationHeader, request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            return switch (scope) {
                case CALCULATIONS -> buildCalculationExportResponse(
                        loadCalculations(uid, request.calculationNameQuery()));
                case PORTFOLIO -> buildPortfolioExportResponse(uid, request.portfolioName());
                case ALL -> buildAllExportResponse(
                        uid,
                        loadCalculations(uid, request.calculationNameQuery()),
                        request.portfolioName());
            };
        } catch (IllegalStateException exception) {
            logger.error("Failed to build export workbook", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate export workbook."));
        }
    }

    private ResponseEntity<byte[]> buildCalculationExportResponse(List<SavedCalculationStore.SavedCalculation> calculations) {
        byte[] bytes = exportWorkbookService.buildCalculationsWorkbook(calculations);
        return fileResponse(bytes, "calculations-export");
    }

    private ResponseEntity<?> buildPortfolioExportResponse(String uid, String portfolioName) {
        if (portfolioName == null || portfolioName.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("portfolioName is required for scope=portfolio."));
        }

        Optional<PortfolioStore.Portfolio> portfolio = portfolioStore.getByName(uid, portfolioName.trim());
        if (portfolio.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Portfolio not found for this user."));
        }

        List<PortfolioStore.LinkedCalculation> linkedCalculations =
                portfolioStore.listLinkedCalculations(uid, portfolio.get().id());
        byte[] bytes = exportWorkbookService.buildPortfolioWorkbook(portfolio.get(), linkedCalculations);
        return fileResponse(bytes, "portfolio-" + normalizeFilePart(portfolio.get().name()));
    }

    private ResponseEntity<?> buildAllExportResponse(String uid,
                                                     List<SavedCalculationStore.SavedCalculation> calculations,
                                                     String portfolioName) {
        List<PortfolioStore.PortfolioSummary> summaries = portfolioStore.listAllSummaries(uid);

        if (portfolioName != null && !portfolioName.isBlank()) {
            String normalized = portfolioName.trim().toLowerCase(Locale.ROOT);
            summaries = summaries.stream()
                    .filter(item -> item.name() != null && item.name().trim().toLowerCase(Locale.ROOT).equals(normalized))
                    .toList();
            if (summaries.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Portfolio not found for this user."));
            }
        }

        List<ExportWorkbookService.PortfolioExportBundle> bundles = new ArrayList<>();
        for (PortfolioStore.PortfolioSummary summary : summaries) {
            List<PortfolioStore.LinkedCalculation> linked = portfolioStore.listLinkedCalculations(uid, summary.id());
            bundles.add(new ExportWorkbookService.PortfolioExportBundle(summary, linked));
        }

        byte[] bytes = exportWorkbookService.buildAllWorkbook(calculations, bundles);
        return fileResponse(bytes, "all-data-export");
    }

    private List<SavedCalculationStore.SavedCalculation> loadCalculations(String uid, String calculationNameQuery) {
        // Keep filtering server-side so exported data matches authorized records exactly.
        if (calculationNameQuery == null || calculationNameQuery.isBlank()) {
            return savedCalculationStore.listByUid(uid);
        }
        return savedCalculationStore.listByUidAndNameLike(uid, calculationNameQuery.trim());
    }

    private ResponseEntity<byte[]> fileResponse(byte[] data, String baseName) {
        // Attachment headers let browsers download the file directly.
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String fileName = baseName + "-" + timestamp + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(EXCEL_MEDIA_TYPE);
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        headers.setCacheControl(CacheControl.noStore().getHeaderValue());
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    private String resolveUid(String authorizationHeader, UidCarrier request) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            FirebaseAuthService.VerifiedFirebaseUser user = verifyUser(authorizationHeader);
            if (user != null) {
                return user.uid();
            }
        }
        if (request == null || request.uid() == null || request.uid().isBlank()) {
            return null;
        }
        return request.uid().trim();
    }

    private FirebaseAuthService.VerifiedFirebaseUser verifyUser(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null) {
            return null;
        }
        try {
            return firebaseAuthService.verifyIdToken(token);
        } catch (FirebaseAuthException exception) {
            logger.warn("Export request rejected: Firebase token verification failed", exception);
            return null;
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.startsWith(prefix) || authorizationHeader.length() <= prefix.length()) {
            return null;
        }
        return authorizationHeader.substring(prefix.length()).trim();
    }

    private String normalizeFilePart(String value) {
        if (value == null || value.isBlank()) {
            return "portfolio";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "portfolio" : normalized;
    }

    public record ExportExcelRequest(
            String uid,
            String scope,
            String calculationNameQuery,
            String portfolioName
    ) implements UidCarrier {
    }

    private sealed interface UidCarrier permits ExportExcelRequest {
        String uid();
    }

    public record ErrorResponse(String message) {
    }

    private enum ExportScope {
        CALCULATIONS,
        PORTFOLIO,
        ALL;

        private static ExportScope from(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (ExportScope scope : values()) {
                if (scope.name().equals(normalized)) {
                    return scope;
                }
            }
            return null;
        }
    }
}
