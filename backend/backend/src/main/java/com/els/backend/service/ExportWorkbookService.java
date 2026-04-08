package com.els.backend.service;

import com.els.backend.database.calculations.SavedCalculationStore;
import com.els.backend.database.portfolios.PortfolioStore;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class ExportWorkbookService {

    public byte[] buildCalculationsWorkbook(List<SavedCalculationStore.SavedCalculation> calculations) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            writeCalculationsSheet(workbook, headerStyle, calculations);
            writeTimeSeriesSheet(workbook, headerStyle, calculations);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build calculations export workbook.", exception);
        }
    }

    public byte[] buildPortfolioWorkbook(PortfolioStore.Portfolio portfolio,
                                         List<PortfolioStore.LinkedCalculation> linkedCalculations) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            writeSinglePortfolioSummarySheet(workbook, headerStyle, portfolio, linkedCalculations);
            writePortfolioItemsSheet(workbook, headerStyle, List.of(new PortfolioExportBundle(
                    new PortfolioStore.PortfolioSummary(
                            portfolio.id(),
                            portfolio.uid(),
                            portfolio.name(),
                            portfolio.description(),
                            portfolio.createdAt(),
                            portfolio.updatedAt(),
                            linkedCalculations.size(),
                            totalPrincipal(linkedCalculations),
                            totalFutureValue(linkedCalculations),
                            averageBeta(linkedCalculations)
                    ),
                    linkedCalculations
            )));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build portfolio export workbook.", exception);
        }
    }

    public byte[] buildAllWorkbook(List<SavedCalculationStore.SavedCalculation> calculations,
                                   List<PortfolioExportBundle> portfolios) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            writeCalculationsSheet(workbook, headerStyle, calculations);
            writeTimeSeriesSheet(workbook, headerStyle, calculations);
            writePortfolioSummariesSheet(workbook, headerStyle, portfolios);
            writePortfolioItemsSheet(workbook, headerStyle, portfolios);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build all-data export workbook.", exception);
        }
    }

    public record PortfolioExportBundle(
            PortfolioStore.PortfolioSummary summary,
            List<PortfolioStore.LinkedCalculation> linkedCalculations
    ) {
    }

    private void writeCalculationsSheet(Workbook workbook,
                                        CellStyle headerStyle,
                                        List<SavedCalculationStore.SavedCalculation> calculations) {
        Sheet sheet = workbook.createSheet("Calculations");
        String[] headers = {
                "Calculation ID", "Name", "Ticker", "Initial Investment", "Years",
                "Beta", "Expected Return", "Future Value", "Created At", "Updated At"
        };
        createHeaderRow(sheet, headers, headerStyle);

        int rowIndex = 1;
        for (SavedCalculationStore.SavedCalculation calculation : calculations) {
            Row row = sheet.createRow(rowIndex++);
            int cell = 0;
            row.createCell(cell++).setCellValue(calculation.id());
            row.createCell(cell++).setCellValue(nullSafe(calculation.name()));
            row.createCell(cell++).setCellValue(nullSafe(calculation.ticker()));
            row.createCell(cell++).setCellValue(calculation.initialInvestment());
            row.createCell(cell++).setCellValue(calculation.years());
            row.createCell(cell++).setCellValue(calculation.beta());
            row.createCell(cell++).setCellValue(calculation.expectedReturn());
            row.createCell(cell++).setCellValue(calculation.futureValue());
            row.createCell(cell++).setCellValue(formatInstant(calculation.createdAt()));
            row.createCell(cell).setCellValue(formatInstant(calculation.updatedAt()));
        }
        autosize(sheet, headers.length);
    }

    private void writeTimeSeriesSheet(Workbook workbook,
                                      CellStyle headerStyle,
                                      List<SavedCalculationStore.SavedCalculation> calculations) {
        Sheet sheet = workbook.createSheet("Calculation_Time_Series");
        String[] headers = {"Calculation ID", "Name", "Ticker", "Year", "Projected Value"};
        createHeaderRow(sheet, headers, headerStyle);

        int rowIndex = 1;
        for (SavedCalculationStore.SavedCalculation calculation : calculations) {
            List<Map.Entry<String, Double>> points = new ArrayList<>(calculation.timeSeries().entrySet());
            points.sort(Comparator.comparingInt(entry -> parseYear(entry.getKey())));
            for (Map.Entry<String, Double> point : points) {
                Row row = sheet.createRow(rowIndex++);
                int cell = 0;
                row.createCell(cell++).setCellValue(calculation.id());
                row.createCell(cell++).setCellValue(nullSafe(calculation.name()));
                row.createCell(cell++).setCellValue(nullSafe(calculation.ticker()));
                row.createCell(cell++).setCellValue(parseYear(point.getKey()));
                row.createCell(cell).setCellValue(point.getValue() == null ? 0.0 : point.getValue());
            }
        }
        autosize(sheet, headers.length);
    }

    private void writeSinglePortfolioSummarySheet(Workbook workbook,
                                                  CellStyle headerStyle,
                                                  PortfolioStore.Portfolio portfolio,
                                                  List<PortfolioStore.LinkedCalculation> linkedCalculations) {
        Sheet sheet = workbook.createSheet("Portfolio_Summary");
        String[] headers = {
                "Portfolio ID", "Name", "Description", "Calculation Count", "Total Principal",
                "Total Future Value", "Average Beta", "Created At", "Updated At"
        };
        createHeaderRow(sheet, headers, headerStyle);

        Row row = sheet.createRow(1);
        int cell = 0;
        row.createCell(cell++).setCellValue(portfolio.id());
        row.createCell(cell++).setCellValue(nullSafe(portfolio.name()));
        row.createCell(cell++).setCellValue(nullSafe(portfolio.description()));
        row.createCell(cell++).setCellValue(linkedCalculations.size());
        row.createCell(cell++).setCellValue(totalPrincipal(linkedCalculations));
        row.createCell(cell++).setCellValue(totalFutureValue(linkedCalculations));
        row.createCell(cell++).setCellValue(averageBeta(linkedCalculations));
        row.createCell(cell++).setCellValue(formatInstant(portfolio.createdAt()));
        row.createCell(cell).setCellValue(formatInstant(portfolio.updatedAt()));
        autosize(sheet, headers.length);
    }

    private void writePortfolioSummariesSheet(Workbook workbook,
                                              CellStyle headerStyle,
                                              List<PortfolioExportBundle> portfolios) {
        Sheet sheet = workbook.createSheet("Portfolios");
        String[] headers = {
                "Portfolio ID", "Name", "Description", "Calculation Count", "Total Principal",
                "Total Future Value", "Average Beta", "Created At", "Updated At"
        };
        createHeaderRow(sheet, headers, headerStyle);

        int rowIndex = 1;
        for (PortfolioExportBundle bundle : portfolios) {
            PortfolioStore.PortfolioSummary summary = bundle.summary();
            Row row = sheet.createRow(rowIndex++);
            int cell = 0;
            row.createCell(cell++).setCellValue(summary.id());
            row.createCell(cell++).setCellValue(nullSafe(summary.name()));
            row.createCell(cell++).setCellValue(nullSafe(summary.description()));
            row.createCell(cell++).setCellValue(summary.calculationCount());
            row.createCell(cell++).setCellValue(summary.totalPrincipal());
            row.createCell(cell++).setCellValue(summary.totalFutureValue());
            row.createCell(cell++).setCellValue(summary.avgBeta());
            row.createCell(cell++).setCellValue(formatInstant(summary.createdAt()));
            row.createCell(cell).setCellValue(formatInstant(summary.updatedAt()));
        }
        autosize(sheet, headers.length);
    }

    private void writePortfolioItemsSheet(Workbook workbook,
                                          CellStyle headerStyle,
                                          List<PortfolioExportBundle> portfolios) {
        Sheet sheet = workbook.createSheet("Portfolio_Items");
        String[] headers = {
                "Portfolio ID", "Portfolio Name", "Calculation ID", "Ticker", "Principal", "Years",
                "Beta", "Expected Return", "CAPM Rate", "Future Value", "Created At", "Updated At"
        };
        createHeaderRow(sheet, headers, headerStyle);

        int rowIndex = 1;
        for (PortfolioExportBundle bundle : portfolios) {
            PortfolioStore.PortfolioSummary summary = bundle.summary();
            for (PortfolioStore.LinkedCalculation linked : bundle.linkedCalculations()) {
                Row row = sheet.createRow(rowIndex++);
                int cell = 0;
                row.createCell(cell++).setCellValue(summary.id());
                row.createCell(cell++).setCellValue(nullSafe(summary.name()));
                row.createCell(cell++).setCellValue(linked.id());
                row.createCell(cell++).setCellValue(nullSafe(linked.ticker()));
                row.createCell(cell++).setCellValue(linked.initialInvestment());
                row.createCell(cell++).setCellValue(linked.years());
                row.createCell(cell++).setCellValue(linked.beta());
                row.createCell(cell++).setCellValue(linked.expectedReturn());
                row.createCell(cell++).setCellValue(FutureValueService.computeCapmRate(
                        linked.beta(), linked.expectedReturn()));
                row.createCell(cell++).setCellValue(linked.futureValue());
                row.createCell(cell++).setCellValue(formatInstant(linked.createdAt()));
                row.createCell(cell).setCellValue(formatInstant(linked.updatedAt()));
            }
        }
        autosize(sheet, headers.length);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void createHeaderRow(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void autosize(Sheet sheet, int totalColumns) {
        for (int i = 0; i < totalColumns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private int parseYear(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private double totalPrincipal(List<PortfolioStore.LinkedCalculation> linkedCalculations) {
        return linkedCalculations.stream()
                .mapToDouble(PortfolioStore.LinkedCalculation::initialInvestment)
                .sum();
    }

    private double totalFutureValue(List<PortfolioStore.LinkedCalculation> linkedCalculations) {
        return linkedCalculations.stream()
                .mapToDouble(PortfolioStore.LinkedCalculation::futureValue)
                .sum();
    }

    private double averageBeta(List<PortfolioStore.LinkedCalculation> linkedCalculations) {
        double principal = totalPrincipal(linkedCalculations);
        if (principal <= 0) {
            return 0.0;
        }
        double weighted = linkedCalculations.stream()
                .mapToDouble(item -> item.beta() * item.initialInvestment())
                .sum();
        return weighted / principal;
    }
}
