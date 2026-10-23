package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;
import com.geointelli.ai.property.service.dto.MortgageCalculationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class MortgageCalculationServiceImplTest {

    MortgageCalculationServiceImpl mortgageCalculationService =
            new MortgageCalculationServiceImpl();

    MortgageCalculationRequest request;

    @BeforeEach
    void setup() {

        request = new MortgageCalculationRequest(
                BigDecimal.valueOf(400000), // propertyValue
                BigDecimal.valueOf(80000),  // downPayment
                BigDecimal.valueOf(3.5),    // interestRate
                30,                          // loanTermYears
                BigDecimal.valueOf(3000),   // annualPropertyTax
                BigDecimal.valueOf(1200),   // annualInsurance
                BigDecimal.valueOf(100)      // monthlyHoaFees
        );

    }

    @Nested
    @DisplayName("CalculateMortgage Tests")
    class CalculateMortgage {

        @DisplayName("When all inputs are provided and valid, then return a MortgageCalculationResponse")
        @Test
        void whenAllInputsAreValid_thenReturnMortgageCalculationResponse() {
            // Arrange

            // Act
            MortgageCalculationResponse response = mortgageCalculationService.calculateMortgage(request);

            // Assert
            assertNotNull(response);
            assertEquals(new BigDecimal("320000.00"), response.loanAmount());
            assertEquals(new BigDecimal("1436.94"), response.monthlyPrincipalAndInterest());
            assertEquals(new BigDecimal("250.00"),  response.monthlyPropertyTax());
            assertEquals(new BigDecimal("100.00"),  response.monthlyInsurance());
            assertEquals(new BigDecimal("100.00"),  response.monthlyHoaFees());
            assertEquals(new BigDecimal("1886.94"), response.totalMonthlyKnownCosts());
        }


        @Test
        @DisplayName("When down payment is greater than property value, then throw IllegalArgumentException")
        void whenDownPaymentGreaterThanPropertyValue_thenThrowIllegalArgumentException() {
            // Arrange
            MortgageCalculationRequest invalidRequest = new MortgageCalculationRequest(
                    BigDecimal.valueOf(400000), // propertyValue
                    BigDecimal.valueOf(500000),  // downPayment (greater than property value)
                    BigDecimal.valueOf(3.5),    // interestRate
                    30,                          // loanTermYears
                    BigDecimal.valueOf(3000),   // annualPropertyTax
                    BigDecimal.valueOf(1200),   // annualInsurance
                    BigDecimal.valueOf(100)      // monthlyHoaFees
            );

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                mortgageCalculationService.calculateMortgage(invalidRequest);
            });
            assertEquals("downPayment must not exceed price", exception.getMessage());
        }

        @Test
        @DisplayName("When interest rate equals zero, then monthly principal and interest should equal loan amount divided by months")
        void whenInterestRateEqualsZero_thenMonthlyPrincipalAndInterestShouldEqualLoanAmountDividedByMonths() {
            // Arrange
            MortgageCalculationRequest zeroInterestRequest = new MortgageCalculationRequest(
                    BigDecimal.valueOf(400000), // propertyValue
                    BigDecimal.valueOf(80000),  // downPayment
                    BigDecimal.ZERO,             // interestRate
                    30,                          // loanTermYears
                    BigDecimal.valueOf(3000),   // annualPropertyTax
                    BigDecimal.valueOf(1200),   // annualInsurance
                    BigDecimal.valueOf(100)      // monthlyHoaFees
            );

            // Act
            MortgageCalculationResponse response = mortgageCalculationService.calculateMortgage(zeroInterestRequest);

            // Assert
            assertNotNull(response);
            assertEquals(new BigDecimal("320000.00"), response.loanAmount());
            assertEquals(new BigDecimal("888.89"), response.monthlyPrincipalAndInterest());
            assertEquals(new BigDecimal("250.00"),  response.monthlyPropertyTax());
            assertEquals(new BigDecimal("100.00"),  response.monthlyInsurance());
            assertEquals(new BigDecimal("100.00"),  response.monthlyHoaFees());
            assertEquals(new BigDecimal("1338.89"), response.totalMonthlyKnownCosts());
        }

        @Test
        @DisplayName("When ownership costs are null, then return null for those fields in the response")
        void whenOwnershipCostsAreNull_thenReturnNullForThoseFields() {
            // Arrange
            MortgageCalculationRequest nullOwnershipCostsRequest = new MortgageCalculationRequest(
                    BigDecimal.valueOf(400000), // propertyValue
                    BigDecimal.valueOf(80000),  // downPayment
                    BigDecimal.valueOf(3.5),    // interestRate
                    30,                          // loanTermYears
                    null,                       // annualPropertyTax
                    null,                       // annualInsurance
                    null                        // monthlyHoaFees
            );

            // Act
            MortgageCalculationResponse response = mortgageCalculationService.calculateMortgage(nullOwnershipCostsRequest);

            // Assert
            assertNotNull(response);
            assertEquals(new BigDecimal("320000.00"), response.loanAmount());
            assertEquals(new BigDecimal("1436.94"), response.monthlyPrincipalAndInterest());
            assertNull(response.monthlyPropertyTax());
            assertNull(response.monthlyInsurance());
            assertNull(response.monthlyHoaFees());
            assertEquals(new BigDecimal("1436.94"), response.totalMonthlyKnownCosts());
        }

        @Test
        @DisplayName("When some ownership costs are null, then return null for those fields in the response")
        void whenSomeOwnershipCostsAreNull_thenReturnNullForThoseFields() {
            // Arrange
            MortgageCalculationRequest nullOwnershipCostsRequest = new MortgageCalculationRequest(
                    BigDecimal.valueOf(400000), // propertyValue
                    BigDecimal.valueOf(80000),  // downPayment
                    BigDecimal.valueOf(3.5),    // interestRate
                    30,                          // loanTermYears
                    BigDecimal.valueOf(3000),   // annualPropertyTax
                    null,
                    null                        // monthlyHoaFees
            );

            // Act
            MortgageCalculationResponse response = mortgageCalculationService.calculateMortgage(nullOwnershipCostsRequest);

            // Assert
            assertNotNull(response);
            assertEquals(new BigDecimal("320000.00"), response.loanAmount());
            assertEquals(new BigDecimal("1436.94"), response.monthlyPrincipalAndInterest());
            assertEquals(new BigDecimal("250.00"), response.monthlyPropertyTax());
            assertNull(response.monthlyInsurance());
            assertNull(response.monthlyHoaFees());
            assertEquals(new BigDecimal("1686.94"), response.totalMonthlyKnownCosts());
        }


        @Test
        @DisplayName("When ownership costs are zero, then return zero for those fields in the response")
        void whenOwnershipCostsAreZero_thenReturnZeroForThoseFields() {
            // Arrange
            MortgageCalculationRequest zeroOwnershipCostsRequest = new MortgageCalculationRequest(
                    BigDecimal.valueOf(400000), // propertyValue
                    BigDecimal.valueOf(80000),  // downPayment
                    BigDecimal.valueOf(3.5),    // interestRate
                    30,                          // loanTermYears
                    BigDecimal.ZERO,             // annualPropertyTax
                    BigDecimal.ZERO,             // annualInsurance
                    BigDecimal.ZERO              // monthlyHoaFees
            );

            // Act
            MortgageCalculationResponse response = mortgageCalculationService.calculateMortgage(zeroOwnershipCostsRequest);

            // Assert
            assertNotNull(response);
            assertEquals(new BigDecimal("320000.00"), response.loanAmount());
            assertEquals(new BigDecimal("1436.94"), response.monthlyPrincipalAndInterest());
            assertEquals(new BigDecimal("0.00"), response.monthlyPropertyTax());
            assertEquals(new BigDecimal("0.00"), response.monthlyInsurance());
            assertEquals(new BigDecimal("0.00"), response.monthlyHoaFees());
            assertEquals(new BigDecimal("1436.94"), response.totalMonthlyKnownCosts());
        }

        @Test
        @DisplayName("When down payment is equal to property value, then loan amount should be zero")
        void whenDownPaymentIsEqualToPropertyValue_thenLoanAmountShouldBeZero() {
            // Arrange
            MortgageCalculationRequest fullDownPaymentRequest = new MortgageCalculationRequest(
                    BigDecimal.valueOf(400000), // propertyValue
                    BigDecimal.valueOf(400000),  // downPayment (equal to property value)
                    BigDecimal.valueOf(3.5),    // interestRate
                    30,                          // loanTermYears
                    BigDecimal.valueOf(3000),   // annualPropertyTax
                    BigDecimal.valueOf(1200),   // annualInsurance
                    BigDecimal.valueOf(100)      // monthlyHoaFees
            );

            // Act
            MortgageCalculationResponse response = mortgageCalculationService.calculateMortgage(fullDownPaymentRequest);

            // Assert
            assertNotNull(response);
            assertEquals(new BigDecimal("0.00"), response.loanAmount());
            assertEquals(new BigDecimal("0.00"), response.monthlyPrincipalAndInterest());
            assertEquals(new BigDecimal("250.00"), response.monthlyPropertyTax());
            assertEquals(new BigDecimal("100.00"), response.monthlyInsurance());
            assertEquals(new BigDecimal("100.00"), response.monthlyHoaFees());
            assertEquals(new BigDecimal("450.00"), response.totalMonthlyKnownCosts());
        }
    }



}
