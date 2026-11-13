package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestmentFinancingRequest;
import com.geointelli.ai.property.service.dto.InvestmentFinancingResult;
import com.geointelli.ai.property.service.dto.InvestmentProjectionRequest;
import com.geointelli.ai.property.service.dto.InvestmentProjectionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InvestmentProjectionServiceImplTest {

    InvestmentProjectionServiceImpl investmentProjectionService = new InvestmentProjectionServiceImpl();

    // 300 000 price, 60 000 down, 6% over 30 years: loan 240 000, annual cash flow -1 097.04
    private static final InvestmentFinancingRequest FINANCING = new InvestmentFinancingRequest(
            BigDecimal.valueOf(300000), BigDecimal.valueOf(60000), BigDecimal.valueOf(6), 30, BigDecimal.valueOf(9000));

    private static final InvestmentFinancingResult FINANCING_RESULT = new InvestmentFinancingResult(
            new BigDecimal("240000.00"), new BigDecimal("1438.92"), new BigDecimal("17267.04"),
            new BigDecimal("-91.42"), new BigDecimal("-1097.04"), new BigDecimal("5.39"),
            new BigDecimal("69000.00"), new BigDecimal("-1.59"), List.of());

    private static final InvestmentFinancingResult NO_FINANCING_RESULT = new InvestmentFinancingResult(
            null, null, null, null, null, null, null, null, List.of());

    @Nested
    @DisplayName("Project Tests")
    class Project {

        @Test
        @DisplayName("When every input is supplied, then project balance, equity parts, total equity and cash flow")
        void whenEveryInputSupplied_thenProjectEverything() {
            // Act
            InvestmentProjectionResult result = investmentProjectionService.project(
                    new InvestmentProjectionRequest(5, BigDecimal.valueOf(3)), FINANCING, FINANCING_RESULT);

            // Assert
            assertEquals(5, result.horizonYears());
            assertEquals(BigDecimal.valueOf(3), result.annualAppreciationRatePercent());
            assertEquals(new BigDecimal("223330.46"), result.remainingLoanBalance());
            assertEquals(new BigDecimal("60000.00"), result.equityFromDownPayment());
            assertEquals(new BigDecimal("16669.54"), result.equityFromLoanPaydown());
            assertEquals(new BigDecimal("347782.22"), result.projectedPropertyValue());
            assertEquals(new BigDecimal("47782.22"), result.equityFromAppreciation());
            assertEquals(new BigDecimal("124451.76"), result.totalEquity());
            assertEquals(new BigDecimal("-5485.20"), result.cumulativeCashFlow());
            assertEquals(3, result.assumptions().size());
            assertTrue(result.assumptions().get(2).contains("supplied by the user"));
            assertTrue(result.notes().isEmpty());
        }

        @Test
        @DisplayName("When the total equity is shown, then it equals the sum of the rounded parts")
        void whenTotalEquityShown_thenEqualSumOfRoundedParts() {
            // Act
            InvestmentProjectionResult result = investmentProjectionService.project(
                    new InvestmentProjectionRequest(5, BigDecimal.valueOf(3)), FINANCING, FINANCING_RESULT);

            // Assert
            assertEquals(result.equityFromDownPayment().add(result.equityFromLoanPaydown())
                    .add(result.equityFromAppreciation()), result.totalEquity());
        }

        @Test
        @DisplayName("When no appreciation rate is supplied, then the value is not projected and no rate is assumed")
        void whenNoAppreciationRate_thenDoNotProjectValue() {
            // Act
            InvestmentProjectionResult result = investmentProjectionService.project(
                    new InvestmentProjectionRequest(5, null), FINANCING, FINANCING_RESULT);

            // Assert
            assertNull(result.annualAppreciationRatePercent());
            assertNull(result.projectedPropertyValue());
            assertNull(result.equityFromAppreciation());
            assertEquals(new BigDecimal("76669.54"), result.totalEquity());
            assertTrue(result.notes().contains(
                    "Appreciation rate not provided: property value not projected; no market appreciation is assumed."));
            assertTrue(result.notes().contains("Total equity excludes appreciation (property value not projected)."));
        }

        @Test
        @DisplayName("When financing is missing, then every financed value is null with a note")
        void whenFinancingMissing_thenFinancedValuesAreNull() {
            // Act
            InvestmentProjectionResult result = investmentProjectionService.project(
                    new InvestmentProjectionRequest(5, BigDecimal.valueOf(3)), null, NO_FINANCING_RESULT);

            // Assert
            assertNull(result.remainingLoanBalance());
            assertNull(result.equityFromDownPayment());
            assertNull(result.equityFromLoanPaydown());
            assertNull(result.projectedPropertyValue());
            assertNull(result.totalEquity());
            assertNull(result.cumulativeCashFlow());
            assertTrue(result.notes().contains("Purchase price missing: property value not projected."));
            assertTrue(result.notes().contains("Total equity unavailable: down payment or loan details missing."));
            assertEquals(1, result.assumptions().size());
        }

        @Test
        @DisplayName("When the horizon is longer than the loan, then the loan is fully repaid")
        void whenHorizonLongerThanLoan_thenBalanceIsZero() {
            // Arrange
            InvestmentFinancingRequest shortLoan = new InvestmentFinancingRequest(
                    BigDecimal.valueOf(300000), BigDecimal.valueOf(60000), BigDecimal.valueOf(6), 10, null);

            // Act
            InvestmentProjectionResult result = investmentProjectionService.project(
                    new InvestmentProjectionRequest(15, null), shortLoan, FINANCING_RESULT);

            // Assert
            assertEquals(new BigDecimal("0.00"), result.remainingLoanBalance());
            assertEquals(new BigDecimal("240000.00"), result.equityFromLoanPaydown());
        }

        @Test
        @DisplayName("When the appreciation rate is negative, then the projected value decreases")
        void whenRateIsNegative_thenValueDecreases() {
            // Act
            InvestmentProjectionResult result = investmentProjectionService.project(
                    new InvestmentProjectionRequest(1, BigDecimal.valueOf(-10)), FINANCING, FINANCING_RESULT);

            // Assert
            assertEquals(new BigDecimal("270000.00"), result.projectedPropertyValue());
            assertEquals(new BigDecimal("-30000.00"), result.equityFromAppreciation());
        }

        @Test
        @DisplayName("When the horizon or the rate is out of range, then throw IllegalArgumentException")
        void whenInputOutOfRange_thenThrowIllegalArgumentException() {
            // Act & Assert
            IllegalArgumentException horizon = assertThrows(IllegalArgumentException.class,
                    () -> investmentProjectionService.project(new InvestmentProjectionRequest(0, null), FINANCING, FINANCING_RESULT));
            assertEquals("horizonYears must be between 1 and 30", horizon.getMessage());
            assertThrows(IllegalArgumentException.class,
                    () -> investmentProjectionService.project(new InvestmentProjectionRequest(31, null), FINANCING, FINANCING_RESULT));
            assertThrows(IllegalArgumentException.class,
                    () -> investmentProjectionService.project(new InvestmentProjectionRequest(null, null), FINANCING, FINANCING_RESULT));
            assertThrows(IllegalArgumentException.class,
                    () -> investmentProjectionService.project(null, FINANCING, FINANCING_RESULT));
            assertThrows(IllegalArgumentException.class,
                    () -> investmentProjectionService.project(
                            new InvestmentProjectionRequest(5, BigDecimal.valueOf(51)), FINANCING, FINANCING_RESULT));
        }
    }
}
