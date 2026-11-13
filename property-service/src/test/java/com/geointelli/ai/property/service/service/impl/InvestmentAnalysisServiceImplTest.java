package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.*;
import com.geointelli.ai.property.service.service.InvestmentProjectionService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class InvestmentAnalysisServiceImplTest {
    // Real operating and financing services; the projection service is spied to check how it is reused
    InvestmentProjectionService investmentProjectionService = spy(new InvestmentProjectionServiceImpl());

    InvestmentAnalysisServiceImpl investmentAnalysisService = new InvestmentAnalysisServiceImpl(
            new InvestmentOperatingServiceImpl(),
            new InvestmentFinancingServiceImpl(new MortgageCalculationServiceImpl(),
                    Validation.buildDefaultValidatorFactory().getValidator()),
            investmentProjectionService);

    private static InvestmentOperatingRequest operating() {
        return new InvestmentOperatingRequest(BigDecimal.valueOf(2500), BigDecimal.valueOf(5), BigDecimal.valueOf(8),
                BigDecimal.valueOf(10), BigDecimal.valueOf(4800), BigDecimal.valueOf(1800), null, BigDecimal.valueOf(50));
    }

    private static InvestmentFinancingRequest financing() {
        return new InvestmentFinancingRequest(BigDecimal.valueOf(300000), BigDecimal.valueOf(60000),
                BigDecimal.valueOf(6), 30, BigDecimal.valueOf(9000));
    }

    @Nested
    @DisplayName("Analyze Tests")
    class Analyze {

        @Test
        @DisplayName("When every section is supplied, then return operating, financing, gross yield and projection")
        void whenEverySectionSupplied_thenReturnCompleteAnalysis() {
            // Act
            InvestmentAnalysisResponse response = investmentAnalysisService.analyze(new InvestmentAnalysisRequest(
                    operating(), financing(), new InvestmentProjectionRequest(5, BigDecimal.valueOf(3))));

            // Assert
            assertEquals(new BigDecimal("16170.00"), response.operating().annualNetOperatingIncome());
            assertEquals(new BigDecimal("1438.92"), response.financing().monthlyDebtService());
            assertEquals(new BigDecimal("-91.42"), response.financing().monthlyCashFlow());
            assertEquals(new BigDecimal("5.39"), response.financing().capRatePercent());
            assertEquals(new BigDecimal("-1.59"), response.financing().cashOnCashPercent());
            assertEquals(new BigDecimal("10.00"), response.grossYieldPercent());
            assertEquals(new BigDecimal("124451.76"), response.projection().totalEquity());
            assertEquals(new BigDecimal("-5485.20"), response.projection().cumulativeCashFlow());
            assertTrue(response.notes().isEmpty());
        }

        @Test
        @DisplayName("When only the rent is supplied, then every missing value is null with a note, nothing assumed")
        void whenOnlyRentSupplied_thenMissingValuesAreNullWithNotes() {
            // Act
            InvestmentAnalysisResponse response = investmentAnalysisService.analyze(new InvestmentAnalysisRequest(
                    new InvestmentOperatingRequest(BigDecimal.valueOf(2500), null, null, null, null, null, null, null),
                    null, null));

            // Assert
            assertEquals(new BigDecimal("30000.00"), response.operating().annualNetOperatingIncome());
            assertNull(response.operating().annualHoaFees());
            assertNull(response.financing().monthlyDebtService());
            assertNull(response.financing().capRatePercent());
            assertNull(response.grossYieldPercent());
            assertNull(response.projection());
            assertTrue(response.notes().contains("No financing section: financed metrics unavailable."));
            assertTrue(response.notes().contains("Purchase price missing: gross yield unavailable."));
            assertTrue(response.notes().contains("No projection requested: equity and appreciation not projected."));
            verifyNoInteractions(investmentProjectionService);
        }

        @Test
        @DisplayName("When a projection is requested, then it reuses the financing result")
        void whenProjectionRequested_thenReuseFinancingResult() {
            // Act
            InvestmentAnalysisResponse response = investmentAnalysisService.analyze(new InvestmentAnalysisRequest(
                    operating(), financing(), new InvestmentProjectionRequest(5, null)));

            // Assert
            verify(investmentProjectionService).project(any(), eq(financing()), eq(response.financing()));
            assertNull(response.projection().projectedPropertyValue());
        }

        @Test
        @DisplayName("When the rent or the operating section is missing, then throw IllegalArgumentException")
        void whenRentMissing_thenThrowIllegalArgumentException() {
            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> investmentAnalysisService.analyze(new InvestmentAnalysisRequest(
                            new InvestmentOperatingRequest(null, null, null, null, null, null, null, null), null, null)));
            assertEquals("operating.monthlyRent is required", exception.getMessage());
            assertThrows(IllegalArgumentException.class,
                    () -> investmentAnalysisService.analyze(new InvestmentAnalysisRequest(null, null, null)));
            assertThrows(IllegalArgumentException.class, () -> investmentAnalysisService.analyze(null));
        }

        @Test
        @DisplayName("When the purchase price is 0, then reject it instead of dividing by zero")
        void whenPurchasePriceIsZero_thenThrowIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> investmentAnalysisService.analyze(new InvestmentAnalysisRequest(operating(),
                            new InvestmentFinancingRequest(BigDecimal.ZERO, null, null, null, null), null)));
        }
    }
}
