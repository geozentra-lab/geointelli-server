package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.*;
import com.geointelli.ai.property.service.service.MortgageCalculationService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class InvestmentFinancingServiceImplTest {

    // Real mortgage calculator, spied to check how it is reused
    MortgageCalculationService mortgageCalculationService = spy(new MortgageCalculationServiceImpl());

    InvestmentFinancingServiceImpl investmentFinancingService =
            new InvestmentFinancingServiceImpl(mortgageCalculationService,
                    Validation.buildDefaultValidatorFactory().getValidator());

    // Annual NOI = 18570.00 (same inputs as InvestmentOperatingServiceImplTest)
    private static final InvestmentOperatingResult OPERATING = new InvestmentOperatingServiceImpl()
            .calculateOperatingCashFlow(new InvestmentOperatingRequest(
                    BigDecimal.valueOf(2500), BigDecimal.valueOf(5), BigDecimal.valueOf(8), BigDecimal.valueOf(10),
                    BigDecimal.valueOf(3600), BigDecimal.valueOf(1200), BigDecimal.ZERO, BigDecimal.ZERO));

    private static InvestmentFinancingRequest fullRequest() {
        return new InvestmentFinancingRequest(
                BigDecimal.valueOf(300000), // purchasePrice
                BigDecimal.valueOf(60000),  // downPayment
                BigDecimal.valueOf(6.5),    // annualInterestRate
                30,                         // loanTermYears
                BigDecimal.valueOf(6000)    // closingCosts
        );
    }

    @Nested
    @DisplayName("CalculateFinancingMetrics Tests")
    class CalculateFinancingMetrics {

        @Test
        @DisplayName("When all inputs are supplied, then return debt service, cash flow, cap rate and cash-on-cash")
        void whenAllInputsAreSupplied_thenReturnAllMetrics() {
            // Act
            InvestmentFinancingResult result = investmentFinancingService.calculateFinancingMetrics(fullRequest(), OPERATING);

            // Assert
            assertEquals(new BigDecimal("240000.00"), result.loanAmount());
            assertEquals(new BigDecimal("1516.96"), result.monthlyDebtService());
            assertEquals(new BigDecimal("18203.52"), result.annualDebtService());
            assertEquals(new BigDecimal("366.48"), result.annualCashFlow());
            assertEquals(new BigDecimal("30.54"), result.monthlyCashFlow());
            assertEquals(new BigDecimal("6.19"), result.capRatePercent());
            assertEquals(new BigDecimal("66000.00"), result.cashInvested());
            assertEquals(new BigDecimal("0.56"), result.cashOnCashPercent());
            assertTrue(result.notes().isEmpty());
        }

        @Test
        @DisplayName("Debt service reuses the mortgage calculator without tax, insurance or HOA (already in the NOI)")
        void whenCalculated_thenReuseMortgageCalculatorWithoutOperatingCosts() {
            // Arrange
            ArgumentCaptor<MortgageCalculationRequest> captor = ArgumentCaptor.forClass(MortgageCalculationRequest.class);

            // Act
            investmentFinancingService.calculateFinancingMetrics(fullRequest(), OPERATING);

            // Assert
            verify(mortgageCalculationService).calculateMortgage(captor.capture());
            MortgageCalculationRequest mortgageRequest = captor.getValue();
            assertEquals(BigDecimal.valueOf(300000), mortgageRequest.price());
            assertEquals(BigDecimal.valueOf(60000), mortgageRequest.downPayment());
            assertNull(mortgageRequest.annualPropertyTax());
            assertNull(mortgageRequest.annualHomeInsurance());
            assertNull(mortgageRequest.monthlyHoaFees());
        }

        @Test
        @DisplayName("When the purchase is paid in cash, then debt service is 0 and cash-on-cash equals cap rate")
        void whenPaidInCash_thenDebtServiceIsZeroAndCashOnCashEqualsCapRate() {
            // Arrange
            InvestmentFinancingRequest request = new InvestmentFinancingRequest(
                    BigDecimal.valueOf(300000), BigDecimal.valueOf(300000), BigDecimal.valueOf(6.5), 30, BigDecimal.ZERO);

            // Act
            InvestmentFinancingResult result = investmentFinancingService.calculateFinancingMetrics(request, OPERATING);

            // Assert
            assertEquals(new BigDecimal("0.00"), result.annualDebtService());
            assertEquals(new BigDecimal("18570.00"), result.annualCashFlow());
            assertEquals(result.capRatePercent(), result.cashOnCashPercent());
            assertTrue(result.notes().isEmpty());
        }

        @Test
        @DisplayName("When financing inputs are missing, then financed metrics are null with a note but cap rate is still returned")
        void whenFinancingInputsAreMissing_thenFinancedMetricsAreNullAndCapRateIsReturned() {
            // Arrange
            InvestmentFinancingRequest request = new InvestmentFinancingRequest(
                    BigDecimal.valueOf(300000), null, null, null, null);

            // Act
            InvestmentFinancingResult result = investmentFinancingService.calculateFinancingMetrics(request, OPERATING);

            // Assert
            assertNull(result.loanAmount());
            assertNull(result.annualDebtService());
            assertNull(result.annualCashFlow());
            assertNull(result.cashOnCashPercent());
            assertEquals(new BigDecimal("6.19"), result.capRatePercent());
            assertEquals(1, result.notes().size());
            assertTrue(result.notes().get(0).contains("Financing inputs missing"));
            verifyNoInteractions(mortgageCalculationService);
        }

        @Test
        @DisplayName("When the purchase price is missing, then cap rate is null with a note")
        void whenPurchasePriceIsMissing_thenCapRateIsNullWithNote() {
            // Arrange
            InvestmentFinancingRequest request = new InvestmentFinancingRequest(
                    null, BigDecimal.valueOf(60000), BigDecimal.valueOf(6.5), 30, BigDecimal.valueOf(6000));

            // Act
            InvestmentFinancingResult result = investmentFinancingService.calculateFinancingMetrics(request, OPERATING);

            // Assert
            assertNull(result.capRatePercent());
            assertNull(result.annualCashFlow());
            assertEquals(new BigDecimal("66000.00"), result.cashInvested());
            assertTrue(result.notes().stream().anyMatch(note -> note.contains("cap rate unavailable")));
        }

        @Test
        @DisplayName("When no cash is invested, then cash-on-cash is null with a note and cash flow may be negative")
        void whenNoCashIsInvested_thenCashOnCashIsNullWithNote() {
            // Arrange
            InvestmentFinancingRequest request = new InvestmentFinancingRequest(
                    BigDecimal.valueOf(300000), BigDecimal.ZERO, BigDecimal.valueOf(6.5), 30, BigDecimal.ZERO);

            // Act
            InvestmentFinancingResult result = investmentFinancingService.calculateFinancingMetrics(request, OPERATING);

            // Assert
            assertEquals(new BigDecimal("0.00"), result.cashInvested());
            assertNull(result.cashOnCashPercent());
            assertEquals(new BigDecimal("-4184.40"), result.annualCashFlow());
            assertEquals(1, result.notes().size());
            assertTrue(result.notes().get(0).contains("No cash invested"));
        }

        @Test
        @DisplayName("When closing costs are missing, then cash invested is the down payment with a note")
        void whenClosingCostsAreMissing_thenCashInvestedIsDownPaymentWithNote() {
            // Arrange
            InvestmentFinancingRequest request = new InvestmentFinancingRequest(
                    BigDecimal.valueOf(300000), BigDecimal.valueOf(60000), BigDecimal.valueOf(6.5), 30, null);

            // Act
            InvestmentFinancingResult result = investmentFinancingService.calculateFinancingMetrics(request, OPERATING);

            // Assert
            assertEquals(new BigDecimal("60000.00"), result.cashInvested());
            assertEquals(new BigDecimal("0.61"), result.cashOnCashPercent());
            assertEquals(1, result.notes().size());
            assertTrue(result.notes().get(0).contains("Closing costs"));
        }

        @Test
        @DisplayName("When the down payment exceeds the purchase price, then throw IllegalArgumentException")
        void whenDownPaymentExceedsPrice_thenThrowIllegalArgumentException() {
            // Arrange
            InvestmentFinancingRequest request = new InvestmentFinancingRequest(
                    BigDecimal.valueOf(300000), BigDecimal.valueOf(400000), BigDecimal.valueOf(6.5), 30, null);

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> investmentFinancingService.calculateFinancingMetrics(request, OPERATING));
            assertEquals("downPayment must not exceed purchasePrice", exception.getMessage());
            verifyNoInteractions(mortgageCalculationService);
        }

        static java.util.stream.Stream<Arguments> invalidRequests() {
            BigDecimal price = BigDecimal.valueOf(300000);
            BigDecimal down = BigDecimal.valueOf(60000);
            BigDecimal rate = BigDecimal.valueOf(6.5);
            return java.util.stream.Stream.of(
                    Arguments.of("purchasePrice", new InvestmentFinancingRequest(BigDecimal.ZERO, null, null, null, null)),
                    Arguments.of("purchasePrice", new InvestmentFinancingRequest(BigDecimal.valueOf(-300000), down, rate, 30, null)),
                    Arguments.of("downPayment", new InvestmentFinancingRequest(price, BigDecimal.valueOf(-50000), rate, 30, null)),
                    Arguments.of("annualInterestRate", new InvestmentFinancingRequest(price, down, BigDecimal.valueOf(-1), 30, null)),
                    Arguments.of("annualInterestRate", new InvestmentFinancingRequest(price, down, BigDecimal.valueOf(500), 30, null)),
                    Arguments.of("loanTermYears", new InvestmentFinancingRequest(price, down, rate, 0, null)),
                    Arguments.of("loanTermYears", new InvestmentFinancingRequest(price, down, rate, -5, null)),
                    Arguments.of("loanTermYears", new InvestmentFinancingRequest(price, down, rate, 51, null)),
                    Arguments.of("closingCosts", new InvestmentFinancingRequest(price, down, rate, 30, BigDecimal.valueOf(-1))),
                    // too large / too many decimal places
                    Arguments.of("purchasePrice", new InvestmentFinancingRequest(new BigDecimal("1e200000"), down, rate, 30, null)),
                    Arguments.of("downPayment", new InvestmentFinancingRequest(price, new BigDecimal("1e-200000"), rate, 30, null)),
                    Arguments.of("annualInterestRate", new InvestmentFinancingRequest(price, down, new BigDecimal("1e-200000"), 30, null)));
        }

        @ParameterizedTest(name = "{0}: {1}")
        @MethodSource("invalidRequests")
        @DisplayName("When a supplied input is invalid, then throw IllegalArgumentException naming it without calculating")
        void whenInputIsInvalid_thenThrowIllegalArgumentException(String field, InvestmentFinancingRequest request) {
            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> investmentFinancingService.calculateFinancingMetrics(request, OPERATING));
            assertTrue(exception.getMessage().contains(field), exception.getMessage());
            verifyNoInteractions(mortgageCalculationService);
        }

        @Test
        @DisplayName("When amounts are at the largest allowed size, then the metrics are calculated")
        void whenAmountsAreAtTheLimit_thenReturnMetrics() {
            // Arrange
            InvestmentFinancingRequest request = new InvestmentFinancingRequest(
                    new BigDecimal("999999999999.99"), new BigDecimal("0.01"), new BigDecimal("99.9999"), 50,
                    new BigDecimal("999999999999.99"));

            // Act
            InvestmentFinancingResult result = investmentFinancingService.calculateFinancingMetrics(request, OPERATING);

            // Assert
            assertNotNull(result.monthlyDebtService());
            assertNotNull(result.cashOnCashPercent());
        }
    }

}
