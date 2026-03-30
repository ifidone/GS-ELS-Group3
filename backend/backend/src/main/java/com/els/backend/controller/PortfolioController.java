package com.els.backend.controller;

import com.els.backend.database.auth.AuthStore;
import com.els.backend.database.portfolios.PortfolioStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/portfolios")
@CrossOrigin(origins = "${app.frontend-origin}")
public class PortfolioController {

    // Portfolio responses are name-based and scoped by uid from request bodies.
    private static final double RISK_FREE_RATE = 0.04;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PortfolioStore portfolioStore;
    private final AuthStore authStore;

    public PortfolioController(PortfolioStore portfolioStore, AuthStore authStore) {
        this.portfolioStore = portfolioStore;
        this.authStore = authStore;
    }

    @GetMapping
    public ResponseEntity<List<PortfolioListItemResponse>> list(
            @RequestBody(required = false) UidRequest request,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        authStore.insertIfMissingUid(uid);

        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        int offset = safePage * safeSize;
        String orderBy = resolveOrderBy(sort);

        List<PortfolioStore.PortfolioSummary> summaries =
                portfolioStore.listSummaries(uid, safeSize, offset, orderBy);

        List<PortfolioListItemResponse> responses = new ArrayList<>();
        for (PortfolioStore.PortfolioSummary summary : summaries) {
            List<PortfolioStore.LinkedCalculation> linked = portfolioStore.listLinkedCalculations(uid, summary.id());
            List<PreviewItem> previews = buildPreviewItems(linked);
            List<AllocationSlice> allocation = buildAllocationBreakdown(linked);
            responses.add(new PortfolioListItemResponse(
                    new PortfolioMetadata(
                            summary.id(),
                            summary.name(),
                            summary.description(),
                            summary.createdAt(),
                            summary.updatedAt()
                    ),
                    new PortfolioSummary(
                            summary.calculationCount(),
                            summary.totalPrincipal(),
                            summary.totalFutureValue(),
                            summary.avgBeta()
                    ),
                    previews,
                    allocation
            ));
        }

        return ResponseEntity.ok(responses);
    }

    @PostMapping
    public ResponseEntity<Object> create(
            @RequestBody(required = false) PortfolioCreateRequest request
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String trimmedName = request.name().trim();
        authStore.insertIfMissingUid(uid);
        if (portfolioStore.portfolioNameExists(uid, trimmedName)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Portfolio with the same name already exists."));
        }

        try {
            PortfolioStore.Portfolio portfolio = portfolioStore.create(
                    uid,
                    trimmedName,
                    request.description()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(toMetadata(portfolio));
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Portfolio with the same name already exists."));
        }
    }

    @GetMapping("/{name}")
    public ResponseEntity<PortfolioDetailResponse> get(
            @PathVariable("name") String name,
            @RequestBody(required = false) PortfolioNameRequest request
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (request == null || request.name() == null || request.name().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!name.trim().equalsIgnoreCase(request.name().trim())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Optional<PortfolioStore.Portfolio> portfolio = portfolioStore.getByName(uid, name.trim());
        if (portfolio.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<PortfolioStore.LinkedCalculation> linked = portfolioStore.listLinkedCalculations(uid, portfolio.get().id());
        PortfolioSummary summary = computeSummary(linked);
        List<ProjectionPoint> projectionPoints = buildProjectionPoints(linked);
        List<AllocationSlice> allocation = buildAllocationBreakdown(linked);
        List<LinkedCalculationResponse> linkedResponses = linked.stream()
                .map(this::toLinkedResponse)
                .toList();

        return ResponseEntity.ok(new PortfolioDetailResponse(
                toMetadata(portfolio.get()),
                summary,
                projectionPoints,
                allocation,
                linkedResponses
        ));
    }

    @PatchMapping("/{name}")
    public ResponseEntity<Object> update(
            @PathVariable("name") String name,
            @RequestBody(required = false) PortfolioUpdateRequest request
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Optional<PortfolioStore.Portfolio> existing = portfolioStore.getByName(uid, name.trim());
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String updatedName = existing.get().name();
        String updatedDescription = existing.get().description();

        if (request.name() != null) {
            if (request.name().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            String trimmedName = request.name().trim();
            if (portfolioStore.portfolioNameExistsExcludingId(uid, trimmedName, existing.get().id())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse("Portfolio with the same name already exists."));
            }
            updatedName = trimmedName;
        }
        if (request.description() != null) {
            updatedDescription = request.description();
        }

        try {
            Optional<PortfolioStore.Portfolio> updated =
                    portfolioStore.updateByName(uid, name.trim(), updatedName, updatedDescription);
            return updated
                    .<ResponseEntity<Object>>map(portfolio -> ResponseEntity.ok((Object) toMetadata(portfolio)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Portfolio with the same name already exists."));
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(
            @PathVariable("name") String name,
            @RequestBody(required = false) UidRequest request
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        boolean deleted = portfolioStore.deleteByName(uid, name.trim());
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @GetMapping("/{portfolioId}/items")
    public ResponseEntity<List<LinkedCalculationResponse>> listItems(
            @PathVariable("portfolioId") String portfolioName,
            @RequestBody(required = false) UidRequest request
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (portfolioName == null || portfolioName.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Optional<Long> portfolioId = portfolioStore.getIdByName(uid, portfolioName.trim());
        if (portfolioId.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<LinkedCalculationResponse> linked = portfolioStore.listLinkedCalculations(uid, portfolioId.get())
                .stream()
                .map(this::toLinkedResponse)
                .toList();
        return ResponseEntity.ok(linked);
    }

    @PutMapping("/{portfolioId}/items/{calculationId}")
    public ResponseEntity<Void> addItem(
            @PathVariable("portfolioId") String portfolioName,
            @PathVariable("calculationId") long calculationId,
            @RequestBody(required = false) UidRequest request
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (portfolioName == null || portfolioName.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Optional<Long> portfolioId = portfolioStore.getIdByName(uid, portfolioName.trim());
        if (portfolioId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!portfolioStore.calculationExistsForUid(uid, calculationId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        portfolioStore.addCalculation(uid, portfolioId.get(), calculationId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{portfolioId}/items/{calculationId}")
    public ResponseEntity<Object> removeItem(
            @PathVariable("portfolioId") String portfolioName,
            @PathVariable("calculationId") long calculationId,
            @RequestBody(required = false) UidRequest request
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (portfolioName == null || portfolioName.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Optional<Long> portfolioId = portfolioStore.getIdByName(uid, portfolioName.trim());
        if (portfolioId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!portfolioStore.calculationExistsForUid(uid, calculationId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Calculation does not exist for this user."));
        }

        boolean removed = portfolioStore.removeCalculation(uid, portfolioId.get(), calculationId);
        if (!removed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Calculation is not linked to this portfolio."));
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{portfolioId}/available-calculations")
    public ResponseEntity<List<LinkedCalculationResponse>> listAvailable(
            @PathVariable("portfolioId") String portfolioName,
            @RequestBody(required = false) UidRequest request
    ) {
        String uid = extractUid(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (portfolioName == null || portfolioName.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Optional<Long> portfolioId = portfolioStore.getIdByName(uid, portfolioName.trim());
        if (portfolioId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<LinkedCalculationResponse> available = portfolioStore.listAvailableCalculations(uid, portfolioId.get())
                .stream()
                .map(this::toLinkedResponse)
                .toList();
        return ResponseEntity.ok(available);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String resolveOrderBy(String sort) {
        if (sort == null || sort.isBlank()) {
            return "p.created_at desc";
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase() : "desc";
        if (!field.equalsIgnoreCase("createdAt")) {
            return "p.created_at desc";
        }
        if (!direction.equals("asc") && !direction.equals("desc")) {
            direction = "desc";
        }
        return "p.created_at " + direction;
    }

    private String extractUid(UidCarrier request) {
        if (request == null || request.uid() == null || request.uid().isBlank()) {
            return null;
        }
        return request.uid().trim();
    }

    private PortfolioMetadata toMetadata(PortfolioStore.Portfolio portfolio) {
        return new PortfolioMetadata(
                portfolio.id(),
                portfolio.name(),
                portfolio.description(),
                portfolio.createdAt(),
                portfolio.updatedAt()
        );
    }

    private LinkedCalculationResponse toLinkedResponse(PortfolioStore.LinkedCalculation calculation) {
        double capm = computeCapmRate(calculation.beta(), calculation.expectedReturn());
        return new LinkedCalculationResponse(
                calculation.id(),
                calculation.ticker(),
                calculation.initialInvestment(),
                calculation.years(),
                calculation.beta(),
                calculation.expectedReturn(),
                capm,
                calculation.futureValue()
        );
    }

    private List<PreviewItem> buildPreviewItems(List<PortfolioStore.LinkedCalculation> linked) {
        return linked.stream()
                .sorted(Comparator.comparingDouble(PortfolioStore.LinkedCalculation::futureValue).reversed())
                .limit(2)
                .map(calculation -> {
                    double capm = computeCapmRate(calculation.beta(), calculation.expectedReturn());
                    return new PreviewItem(
                            calculation.id(),
                            calculation.ticker(),
                            calculation.initialInvestment(),
                            calculation.years(),
                            calculation.beta(),
                            calculation.expectedReturn(),
                            capm,
                            calculation.futureValue()
                    );
                })
                .toList();
    }

    private List<AllocationSlice> buildAllocationBreakdown(List<PortfolioStore.LinkedCalculation> linked) {
        Map<String, Double> totalsByLabel = new LinkedHashMap<>();
        for (PortfolioStore.LinkedCalculation calculation : linked) {
            String label = calculation.ticker();
            totalsByLabel.put(label, totalsByLabel.getOrDefault(label, 0.0) + calculation.futureValue());
        }

        List<Map.Entry<String, Double>> sorted = totalsByLabel.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();

        double total = sorted.stream().mapToDouble(Map.Entry::getValue).sum();
        List<AllocationSlice> slices = new ArrayList<>();
        for (Map.Entry<String, Double> entry : sorted) {
            double percentage = total > 0 ? entry.getValue() / total : 0;
            slices.add(new AllocationSlice(entry.getKey(), entry.getValue(), percentage));
        }
        return slices;
    }

    private PortfolioSummary computeSummary(List<PortfolioStore.LinkedCalculation> linked) {
        if (linked.isEmpty()) {
            return new PortfolioSummary(0, 0, 0, 0);
        }

        double totalPrincipal = 0;
        double totalFutureValue = 0;
        double weightedBeta = 0;
        for (PortfolioStore.LinkedCalculation calculation : linked) {
            totalPrincipal += calculation.initialInvestment();
            totalFutureValue += calculation.futureValue();
            weightedBeta += calculation.beta() * calculation.initialInvestment();
        }

        double avgBeta = totalPrincipal > 0 ? weightedBeta / totalPrincipal : 0;
        return new PortfolioSummary(linked.size(), totalPrincipal, totalFutureValue, avgBeta);
    }

    private List<ProjectionPoint> buildProjectionPoints(List<PortfolioStore.LinkedCalculation> linked) {
        double maxYears = linked.stream()
                .mapToDouble(PortfolioStore.LinkedCalculation::years)
                .max()
                .orElse(0);
        int maxYearsInt = (int) Math.ceil(maxYears);
        if (maxYearsInt <= 0) {
            return List.of();
        }

        List<ProjectionPoint> points = new ArrayList<>();
        for (int year = 0; year <= maxYearsInt; year++) {
            double total = 0;
            for (PortfolioStore.LinkedCalculation calculation : linked) {
                double rate = computeCapmRate(calculation.beta(), calculation.expectedReturn());
                double cappedTime = Math.min(year, calculation.years());
                total += calculation.initialInvestment() * Math.exp(rate * cappedTime);
            }
            points.add(new ProjectionPoint(year, total));
        }
        return points;
    }

    private double computeCapmRate(double beta, double expectedReturn) {
        return RISK_FREE_RATE + beta * (expectedReturn - RISK_FREE_RATE);
    }

    public record PortfolioCreateRequest(String uid, String name, String description) implements UidCarrier {
    }

    public record PortfolioUpdateRequest(String uid, String name, String description) implements UidCarrier {
    }

    public record UidRequest(String uid) implements UidCarrier {
    }

    public record PortfolioNameRequest(String uid, String name) implements UidCarrier {
    }

    private sealed interface UidCarrier permits PortfolioCreateRequest, PortfolioUpdateRequest, UidRequest, PortfolioNameRequest {
        String uid();
    }

    public record PortfolioMetadata(
            long id,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PortfolioSummary(
            long calculationCount,
            double totalPrincipal,
            double totalFutureValue,
            double avgBeta
    ) {
    }

    public record PortfolioListItemResponse(
            PortfolioMetadata metadata,
            PortfolioSummary summary,
            List<PreviewItem> previewItems,
            List<AllocationSlice> allocationBreakdown
    ) {
    }

    public record PortfolioDetailResponse(
            PortfolioMetadata metadata,
            PortfolioSummary summary,
            List<ProjectionPoint> projectionPoints,
            List<AllocationSlice> allocationBreakdown,
            List<LinkedCalculationResponse> linkedCalculations
    ) {
    }

    public record PreviewItem(
            long calculationId,
            String ticker,
            double principal,
            double years,
            double beta,
            double expectedReturn,
            double capm,
            double futureValue
    ) {
    }

    public record LinkedCalculationResponse(
            long calculationId,
            String ticker,
            double principal,
            double years,
            double beta,
            double expectedReturn,
            double capm,
            double futureValue
    ) {
    }

    public record AllocationSlice(
            String label,
            double totalFutureValue,
            double percentage
    ) {
    }

    public record ProjectionPoint(
            double year,
            double totalFutureValue
    ) {
    }

    public record ErrorResponse(String message) {
    }
}
