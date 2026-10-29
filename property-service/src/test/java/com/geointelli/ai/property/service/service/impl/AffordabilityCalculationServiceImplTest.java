package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.AffordabilityCalculationRequest;
import com.geointelli.ai.property.service.dto.AffordabilityCalculationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AffordabilityCalculationServiceImplTest {
    AffordabilityCalculationServiceImpl affordabilityCalculationService =
            new AffordabilityCalculationServiceImpl();

    private static AffordabilityCalculationRequest requestWithoutCosts() {
        return new AffordabilityCalculationRequest(
                BigDecimal.valueOf(72000), // annualGrossIncome
                BigDecimal.valueOf(500),   // monthlyDebtPayments
                BigDecimal.valueOf(50000), // downPayment
                BigDecimal.valueOf(6.5),   // annualInterestRate
                30,                         // loanTermYears
                null, null, null            // tax, insurance, HOA
        );
    }

    @Nested
    @DisplayName("CalculateAffordability Tests")
    class CalculateAffordability {

        @Test
        @DisplayName("When costs are not supplied, then estimate uses the whole housing budget for the loan")
        void whenCostsAreNotSupplied_thenReturnEstimate() {
            // Act
            AffordabilityCalculationResponse response =
                    affordabilityCalculationService.calculateAffordability(requestWithoutCosts());

            // Assert
            assertEquals(new BigDecimal("6000.00"), response.monthlyGrossIncome());
            assertEquals(new BigDecimal("1660.00"), response.maxMonthlyHousingPayment());
            assertEquals(new BigDecimal("1660.00"), response.maxMonthlyPrincipalAndInterest());
            assertEquals(new BigDecimal("262629.96"), response.maxLoanAmount());
            assertEquals(new BigDecimal("312629.96"), response.maxHomePrice());
        }

        @Test
        @DisplayName("When costs are not supplied, then one note is returned per missing cost")
        void whenCostsAreNotSupplied_thenReturnOneNotePerMissingCost() {
            // Act
            AffordabilityCalculationResponse response =
                    affordabilityCalculationService.calculateAffordability(requestWithoutCosts());

            // Assert
            assertEquals(3, response.notes().size());
            assertTrue(response.notes().get(0).contains("Property tax"));
            assertTrue(response.notes().get(1).contains("Home insurance"));
            assertTrue(response.notes().get(2).contains("HOA"));
        }

        @Test
        @DisplayName("When all costs are supplied, then they are deducted from the budget and no note is returned")
        void whenAllCostsAreSupplied_thenDeductThemAndReturnNoNote() {
            // Arrange: rate 0 so the loan is simply payment x 360
            AffordabilityCalculationRequest request = new AffordabilityCalculationRequest(
                    BigDecimal.valueOf(72000),
                    BigDecimal.valueOf(500),
                    BigDecimal.valueOf(50000),
                    BigDecimal.ZERO,
                    30,
                    BigDecimal.valueOf(3600), // 300 / month
                    BigDecimal.valueOf(1200), // 100 / month
                    BigDecimal.valueOf(100)   // 100 / month
            );

            // Act
            AffordabilityCalculationResponse response = affordabilityCalculationService.calculateAffordability(request);

            // Assert
            assertEquals(new BigDecimal("1660.00"), response.maxMonthlyHousingPayment());
            assertEquals(new BigDecimal("1160.00"), response.maxMonthlyPrincipalAndInterest());
            assertEquals(new BigDecimal("417600.00"), response.maxLoanAmount());
            assertEquals(new BigDecimal("467600.00"), response.maxHomePrice());
            assertTrue(response.notes().isEmpty());
        }

        @Test
        @DisplayName("When yearly income is not a multiple of 12, then monthly income keeps its cents")
        void whenIncomeIsNotMultipleOfTwelve_thenMonthlyIncomeKeepsCents() {
            // Arrange
            AffordabilityCalculationRequest request = new AffordabilityCalculationRequest(
                    BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(6.5), 30,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            // Act
            AffordabilityCalculationResponse response = affordabilityCalculationService.calculateAffordability(request);

            // Assert
            assertEquals(new BigDecimal("4166.67"), response.monthlyGrossIncome());
        }

        @Test
        @DisplayName("When debts exceed the back-end limit, then nothing is affordable and a note explains why")
        void whenDebtsExceedBackEndLimit_thenReturnZeroWithNote() {
            // Arrange
            AffordabilityCalculationRequest request = new AffordabilityCalculationRequest(
                    BigDecimal.valueOf(72000), BigDecimal.valueOf(3000), BigDecimal.ZERO, BigDecimal.valueOf(6.5), 30,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            // Act
            AffordabilityCalculationResponse response = affordabilityCalculationService.calculateAffordability(request);

            // Assert
            assertEquals(new BigDecimal("0.00"), response.maxMonthlyHousingPayment());
            assertEquals(new BigDecimal("0.00"), response.maxLoanAmount());
            assertEquals(new BigDecimal("0.00"), response.maxHomePrice());
            assertEquals(1, response.notes().size());
            assertTrue(response.notes().get(0).contains("36%"));
        }

        @Test
        @DisplayName("When known costs exceed the housing budget, then no loan is affordable and price equals down payment")
        void whenCostsExceedBudget_thenNoLoanAndPriceEqualsDownPayment() {
            // Arrange: 24000 / year = 2000 / month > 1660 budget
            AffordabilityCalculationRequest request = new AffordabilityCalculationRequest(
                    BigDecimal.valueOf(72000), BigDecimal.valueOf(500), BigDecimal.valueOf(50000), BigDecimal.valueOf(6.5), 30,
                    BigDecimal.valueOf(24000), BigDecimal.ZERO, BigDecimal.ZERO);

            // Act
            AffordabilityCalculationResponse response = affordabilityCalculationService.calculateAffordability(request);

            // Assert
            assertEquals(new BigDecimal("0.00"), response.maxMonthlyPrincipalAndInterest());
            assertEquals(new BigDecimal("0.00"), response.maxLoanAmount());
            assertEquals(new BigDecimal("50000.00"), response.maxHomePrice());
            assertEquals(1, response.notes().size());
        }

        @Test
        @DisplayName("Response always contains the assumptions used and the disclaimer")
        void whenCalculated_thenReturnAssumptionsAndDisclaimer() {
            // Act
            AffordabilityCalculationResponse response =
                    affordabilityCalculationService.calculateAffordability(requestWithoutCosts());

            // Assert
            assertEquals(0, BigDecimal.valueOf(28).compareTo(response.assumptions().frontEndRatioPercent()));
            assertEquals(0, BigDecimal.valueOf(36).compareTo(response.assumptions().backEndRatioPercent()));
            assertEquals(BigDecimal.valueOf(6.5), response.assumptions().annualInterestRate());
            assertEquals(30, response.assumptions().loanTermYears());
            assertNotNull(response.assumptions().rateSource());
            assertTrue(response.disclaimer().contains("not a loan approval"));
        }
    }
}
