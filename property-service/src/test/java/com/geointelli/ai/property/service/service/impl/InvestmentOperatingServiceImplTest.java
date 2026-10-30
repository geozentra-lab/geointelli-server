package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestmentOperatingRequest;
import com.geointelli.ai.property.service.dto.InvestmentOperatingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class InvestmentOperatingServiceImplTest {

    InvestmentOperatingServiceImpl investmentOperatingService = new InvestmentOperatingServiceImpl();

    private static InvestmentOperatingRequest fullRequest() {
        return new InvestmentOperatingRequest(
                BigDecimal.valueOf(2500), // monthlyRent
                BigDecimal.valueOf(5),    // vacancyRatePercent
                BigDecimal.valueOf(8),    // maintenanceRatePercent
                BigDecimal.valueOf(10),   // managementRatePercent
                BigDecimal.valueOf(3600), // annualPropertyTax
                BigDecimal.valueOf(1200), // annualHomeInsurance
                BigDecimal.ZERO,          // monthlyHoaFees
                BigDecimal.ZERO           // monthlyOtherExpenses
        );
    }

    private static InvestmentOperatingRequest rentOnlyRequest() {
        return new InvestmentOperatingRequest(
                BigDecimal.valueOf(2500), null, null, null, null, null, null, null);
    }

    @Nested
    @DisplayName("CalculateOperatingCashFlow Tests")
    class CalculateOperatingCashFlow {

        @Test
        @DisplayName("When all inputs are supplied, then return monthly and annual income, expenses and NOI")
        void whenAllInputsAreSupplied_thenReturnIncomeExpensesAndNoi() {
            // Act
            InvestmentOperatingResult result = investmentOperatingService.calculateOperatingCashFlow(fullRequest());

            // Assert
            assertEquals(new BigDecimal("2500.00"), result.monthlyGrossRent());
            assertEquals(new BigDecimal("30000.00"), result.annualGrossRent());
            assertEquals(new BigDecimal("125.00"), result.monthlyVacancyLoss());
            assertEquals(new BigDecimal("1500.00"), result.annualVacancyLoss());
            assertEquals(new BigDecimal("2375.00"), result.monthlyEffectiveIncome());
            assertEquals(new BigDecimal("28500.00"), result.annualEffectiveIncome());
            assertEquals(new BigDecimal("827.50"), result.monthlyOperatingExpenses());
            assertEquals(new BigDecimal("9930.00"), result.annualOperatingExpenses());
            assertEquals(new BigDecimal("1547.50"), result.monthlyNetOperatingIncome());
            assertEquals(new BigDecimal("18570.00"), result.annualNetOperatingIncome());
            assertTrue(result.notes().isEmpty());
        }

        @Test
        @DisplayName("When all inputs are supplied, then return the annual expense breakdown")
        void whenAllInputsAreSupplied_thenReturnExpenseBreakdown() {
            // Act
            InvestmentOperatingResult result = investmentOperatingService.calculateOperatingCashFlow(fullRequest());

            // Assert: maintenance and management are based on effective income (28500)
            assertEquals(new BigDecimal("2280.00"), result.annualMaintenance());
            assertEquals(new BigDecimal("2850.00"), result.annualManagement());
            assertEquals(new BigDecimal("3600.00"), result.annualPropertyTax());
            assertEquals(new BigDecimal("1200.00"), result.annualInsurance());
            assertEquals(new BigDecimal("0.00"), result.annualHoaFees());
            assertEquals(new BigDecimal("0.00"), result.annualOtherExpenses());
        }

        @Test
        @DisplayName("When only rent is supplied, then missing values are null and one note is returned per missing value")
        void whenOnlyRentIsSupplied_thenMissingValuesAreNullWithNotes() {
            // Act
            InvestmentOperatingResult result = investmentOperatingService.calculateOperatingCashFlow(rentOnlyRequest());

            // Assert
            assertNull(result.monthlyVacancyLoss());
            assertNull(result.annualVacancyLoss());
            assertNull(result.annualMaintenance());
            assertNull(result.annualManagement());
            assertNull(result.annualPropertyTax());
            assertNull(result.annualInsurance());
            assertNull(result.annualHoaFees());
            assertNull(result.annualOtherExpenses());
            assertEquals(7, result.notes().size());
        }

        @Test
        @DisplayName("When only rent is supplied, then income and NOI equal the gross rent")
        void whenOnlyRentIsSupplied_thenNoiEqualsGrossRent() {
            // Act
            InvestmentOperatingResult result = investmentOperatingService.calculateOperatingCashFlow(rentOnlyRequest());

            // Assert
            assertEquals(new BigDecimal("30000.00"), result.annualEffectiveIncome());
            assertEquals(new BigDecimal("0.00"), result.annualOperatingExpenses());
            assertEquals(new BigDecimal("30000.00"), result.annualNetOperatingIncome());
        }

        @Test
        @DisplayName("When HOA is 0, then it is 0 without note; when HOA is missing, then it is null with a note")
        void whenHoaIsZeroOrMissing_thenBothCasesAreDistinguished() {
            // Act
            InvestmentOperatingResult withZeroHoa = investmentOperatingService.calculateOperatingCashFlow(fullRequest());
            InvestmentOperatingResult withoutHoa = investmentOperatingService.calculateOperatingCashFlow(rentOnlyRequest());

            // Assert
            assertEquals(new BigDecimal("0.00"), withZeroHoa.annualHoaFees());
            assertTrue(withZeroHoa.notes().stream().noneMatch(note -> note.contains("HOA")));

            assertNull(withoutHoa.annualHoaFees());
            assertTrue(withoutHoa.notes().stream().anyMatch(note -> note.contains("HOA")));
        }

        @Test
        @DisplayName("When monthly costs are supplied, then they are converted to annual amounts")
        void whenMonthlyCostsAreSupplied_thenConvertToAnnual() {
            // Arrange
            InvestmentOperatingRequest request = new InvestmentOperatingRequest(
                    BigDecimal.valueOf(2500), null, null, null, null, null,
                    BigDecimal.valueOf(150), // monthlyHoaFees
                    BigDecimal.valueOf(50)   // monthlyOtherExpenses
            );

            // Act
            InvestmentOperatingResult result = investmentOperatingService.calculateOperatingCashFlow(request);

            // Assert
            assertEquals(new BigDecimal("1800.00"), result.annualHoaFees());
            assertEquals(new BigDecimal("600.00"), result.annualOtherExpenses());
            assertEquals(new BigDecimal("2400.00"), result.annualOperatingExpenses());
            assertEquals(new BigDecimal("200.00"), result.monthlyOperatingExpenses());
        }

        @Test
        @DisplayName("When expenses exceed income, then NOI is negative")
        void whenExpensesExceedIncome_thenNoiIsNegative() {
            // Arrange
            InvestmentOperatingRequest request = new InvestmentOperatingRequest(
                    BigDecimal.valueOf(500), null, null, null, BigDecimal.valueOf(12000), null, null, null);

            // Act
            InvestmentOperatingResult result = investmentOperatingService.calculateOperatingCashFlow(request);

            // Assert
            assertEquals(new BigDecimal("-6000.00"), result.annualNetOperatingIncome());
            assertEquals(new BigDecimal("-500.00"), result.monthlyNetOperatingIncome());
        }

        @Test
        @DisplayName("When rent is 0, then income is 0 and percentage-based costs are 0")
        void whenRentIsZero_thenIncomeAndPercentageCostsAreZero() {
            // Arrange
            InvestmentOperatingRequest request = new InvestmentOperatingRequest(
                    BigDecimal.ZERO, BigDecimal.valueOf(5), BigDecimal.valueOf(8), BigDecimal.valueOf(10),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            // Act
            InvestmentOperatingResult result = investmentOperatingService.calculateOperatingCashFlow(request);

            // Assert
            assertEquals(new BigDecimal("0.00"), result.annualEffectiveIncome());
            assertEquals(new BigDecimal("0.00"), result.annualMaintenance());
            assertEquals(new BigDecimal("0.00"), result.annualManagement());
            assertEquals(new BigDecimal("0.00"), result.annualNetOperatingIncome());
        }
    }

}
