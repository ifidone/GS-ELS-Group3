package com.els.backend.service;

import com.els.backend.database.calculations.SavedCalculationStore;
import com.els.backend.database.portfolios.PortfolioStore;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExportWorkbookServiceTest {

    private final ExportWorkbookService service = new ExportWorkbookService();

    @Test
    void buildCalculationsWorkbook_containsExpectedSheetsAndHeaders() throws Exception {
        SavedCalculationStore.SavedCalculation calculation = new SavedCalculationStore.SavedCalculation(
                1L,
                "uid-1",
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

        byte[] bytes = service.buildCalculationsWorkbook(List.of(calculation));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Calculations"));
            assertNotNull(workbook.getSheet("Calculation_Time_Series"));
            assertEquals("Calculation ID", workbook.getSheet("Calculations").getRow(0).getCell(0).getStringCellValue());
            assertEquals("Projected Value",
                    workbook.getSheet("Calculation_Time_Series").getRow(0).getCell(4).getStringCellValue());
        }
    }

    @Test
    void buildPortfolioWorkbook_containsSummaryAndItemsSheets() throws Exception {
        PortfolioStore.Portfolio portfolio = new PortfolioStore.Portfolio(
                10L,
                "uid-1",
                "Growth",
                "Long term",
                Instant.now(),
                Instant.now()
        );
        PortfolioStore.LinkedCalculation linked = new PortfolioStore.LinkedCalculation(
                99L,
                "SWPPX",
                5000,
                5,
                1.0,
                0.05,
                6400,
                Instant.now(),
                Instant.now()
        );

        byte[] bytes = service.buildPortfolioWorkbook(portfolio, List.of(linked));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Portfolio_Summary"));
            assertNotNull(workbook.getSheet("Portfolio_Items"));
            assertEquals("Portfolio ID", workbook.getSheet("Portfolio_Summary").getRow(0).getCell(0).getStringCellValue());
            assertEquals("CAPM Rate", workbook.getSheet("Portfolio_Items").getRow(0).getCell(8).getStringCellValue());
        }
    }

    @Test
    void buildAllWorkbook_containsAllSheets() throws Exception {
        SavedCalculationStore.SavedCalculation calculation = new SavedCalculationStore.SavedCalculation(
                3L,
                "uid-1",
                "Income",
                "FDGRX",
                15000,
                8,
                1.1,
                0.07,
                26000,
                Map.of("0", 15000.0, "8", 26000.0),
                Instant.now(),
                Instant.now()
        );
        PortfolioStore.PortfolioSummary summary = new PortfolioStore.PortfolioSummary(
                7L,
                "uid-1",
                "Portfolio A",
                "A",
                Instant.now(),
                Instant.now(),
                1,
                15000,
                26000,
                1.1
        );
        PortfolioStore.LinkedCalculation linked = new PortfolioStore.LinkedCalculation(
                3L,
                "FDGRX",
                15000,
                8,
                1.1,
                0.07,
                26000,
                Instant.now(),
                Instant.now()
        );

        byte[] bytes = service.buildAllWorkbook(
                List.of(calculation),
                List.of(new ExportWorkbookService.PortfolioExportBundle(summary, List.of(linked)))
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Calculations"));
            assertNotNull(workbook.getSheet("Calculation_Time_Series"));
            assertNotNull(workbook.getSheet("Portfolios"));
            assertNotNull(workbook.getSheet("Portfolio_Items"));
        }
    }
}
