package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.AffordabilityAssumptions;
import com.geointelli.ai.property.service.dto.AffordabilityCalculationRequest;
import com.geointelli.ai.property.service.dto.AffordabilityCalculationResponse;
import com.geointelli.ai.property.service.dto.AffordabilityCardDTO;
import com.geointelli.ai.property.service.service.AffordabilityCalculationService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserDashboardAffordabilityServiceImplTest {

    AffordabilityCalculationService affordabilityCalculationService = mock(AffordabilityCalculationService.class);

    UserDashboardAffordabilityServiceImpl affordabilityCardService = new UserDashboardAffordabilityServiceImpl(
            affordabilityCalculationService, Validation.buildDefaultValidatorFactory().getValidator());

    private static AffordabilityCalculationRequest validInputs() {
        return new AffordabilityCalculationRequest(
                BigDecimal.valueOf(72000), // annualGrossIncome
                BigDecimal.valueOf(500),   // monthlyDebtPayments
                BigDecimal.valueOf(50000), // downPayment
                BigDecimal.valueOf(6.5),   // annualInterestRate
                30,                         // loanTermYears
                null, null, null            // tax, insurance, HOA
        );
    }

    private static AffordabilityCalculationResponse affordabilityResult() {
        return new AffordabilityCalculationResponse(
                new BigDecimal("6000.00"), new BigDecimal("1660.00"), new BigDecimal("1660.00"),
                new BigDecimal("262629.96"), new BigDecimal("312629.96"),
                new AffordabilityAssumptions(BigDecimal.valueOf(28), BigDecimal.valueOf(36),
                        BigDecimal.valueOf(6.5), 30, "Supplied by user, not a market rate"),
                List.of(), "This is an affordability estimate, not a loan approval.");
    }

    @Nested
    @DisplayName("GetAffordabilityCard Tests")
    class GetAffordabilityCard {

        @Test
        @DisplayName("When required inputs are supplied, then return OK with the reused affordability result")
        void whenRequiredInputsAreSupplied_thenReturnOkCard() {
            // Arrange
            AffordabilityCalculationRequest inputs = validInputs();
            AffordabilityCalculationResponse affordability = affordabilityResult();
            when(affordabilityCalculationService.calculateAffordability(inputs)).thenReturn(affordability);

            // Act
            AffordabilityCardDTO card = affordabilityCardService.getAffordabilityCard(inputs);

            // Assert
            assertEquals(AffordabilityCardDTO.STATUS_OK, card.getStatus());
            assertEquals(AffordabilityCardDTO.SCOPE_CURRENT_USER, card.getScope());
            assertNull(card.getError());
            assertTrue(card.getMissingInputs().isEmpty());
            assertSame(affordability, card.getAffordability());
            assertNotNull(card.getGeneratedAt());
            verify(affordabilityCalculationService).calculateAffordability(inputs);
        }

        @Test
        @DisplayName("When no input is supplied, then return INCOMPLETE listing every required input")
        void whenNoInputIsSupplied_thenReturnIncompleteWithAllRequiredInputs() {
            // Arrange
            AffordabilityCalculationRequest inputs = new AffordabilityCalculationRequest(
                    null, null, null, null, null, null, null, null);

            // Act
            AffordabilityCardDTO card = affordabilityCardService.getAffordabilityCard(inputs);

            // Assert
            assertEquals(AffordabilityCardDTO.STATUS_INCOMPLETE, card.getStatus());
            assertEquals(UserDashboardAffordabilityServiceImpl.INCOMPLETE_ERROR, card.getError());
            assertEquals(List.of("annualGrossIncome", "monthlyDebtPayments", "downPayment",
                    "annualInterestRate", "loanTermYears"), card.getMissingInputs());
            assertNull(card.getAffordability());
            assertNotNull(card.getGeneratedAt());
            verifyNoInteractions(affordabilityCalculationService);
        }

        @Test
        @DisplayName("When inputs are null, then return INCOMPLETE listing every required input")
        void whenInputsAreNull_thenReturnIncompleteWithAllRequiredInputs() {
            // Act
            AffordabilityCardDTO card = affordabilityCardService.getAffordabilityCard(null);

            // Assert
            assertEquals(AffordabilityCardDTO.STATUS_INCOMPLETE, card.getStatus());
            assertEquals(5, card.getMissingInputs().size());
            verifyNoInteractions(affordabilityCalculationService);
        }

        @Test
        @DisplayName("When some required inputs are missing, then return INCOMPLETE listing only those inputs")
        void whenSomeRequiredInputsAreMissing_thenReturnIncompleteWithMissingOnes() {
            // Arrange: debts and rate never supplied, they are not assumed to be 0
            AffordabilityCalculationRequest inputs = new AffordabilityCalculationRequest(
                    BigDecimal.valueOf(72000), null, BigDecimal.valueOf(50000), null, 30, null, null, null);

            // Act
            AffordabilityCardDTO card = affordabilityCardService.getAffordabilityCard(inputs);

            // Assert
            assertEquals(AffordabilityCardDTO.STATUS_INCOMPLETE, card.getStatus());
            assertEquals(List.of("monthlyDebtPayments", "annualInterestRate"), card.getMissingInputs());
            assertNull(card.getAffordability());
            verifyNoInteractions(affordabilityCalculationService);
        }

        @Test
        @DisplayName("When only optional costs are missing, then the card is not INCOMPLETE")
        void whenOnlyOptionalCostsAreMissing_thenCardIsOk() {
            // Arrange
            AffordabilityCalculationRequest inputs = validInputs();
            when(affordabilityCalculationService.calculateAffordability(inputs)).thenReturn(affordabilityResult());

            // Act
            AffordabilityCardDTO card = affordabilityCardService.getAffordabilityCard(inputs);

            // Assert
            assertEquals(AffordabilityCardDTO.STATUS_OK, card.getStatus());
            assertTrue(card.getMissingInputs().isEmpty());
        }

        @Test
        @DisplayName("When the rate is above 100, then throw IllegalArgumentException without calling the calculator")
        void whenRateIsAbove100_thenThrowIllegalArgumentException() {
            // Arrange
            AffordabilityCalculationRequest inputs = new AffordabilityCalculationRequest(
                    BigDecimal.valueOf(72000), BigDecimal.valueOf(500), BigDecimal.valueOf(50000),
                    BigDecimal.valueOf(150), 30, null, null, null);

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> affordabilityCardService.getAffordabilityCard(inputs));
            assertTrue(exception.getMessage().contains("annualInterestRate"));
            verifyNoInteractions(affordabilityCalculationService);
        }

        @Test
        @DisplayName("When income is 0, then throw IllegalArgumentException without calling the calculator")
        void whenIncomeIsZero_thenThrowIllegalArgumentException() {
            // Arrange
            AffordabilityCalculationRequest inputs = new AffordabilityCalculationRequest(
                    BigDecimal.ZERO, BigDecimal.valueOf(500), BigDecimal.valueOf(50000),
                    BigDecimal.valueOf(6.5), 30, null, null, null);

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> affordabilityCardService.getAffordabilityCard(inputs));
            assertTrue(exception.getMessage().contains("annualGrossIncome"));
            verifyNoInteractions(affordabilityCalculationService);
        }

        @Test
        @DisplayName("When an optional cost is negative, then throw IllegalArgumentException")
        void whenOptionalCostIsNegative_thenThrowIllegalArgumentException() {
            // Arrange
            AffordabilityCalculationRequest inputs = new AffordabilityCalculationRequest(
                    BigDecimal.valueOf(72000), BigDecimal.valueOf(500), BigDecimal.valueOf(50000),
                    BigDecimal.valueOf(6.5), 30, BigDecimal.valueOf(-5), null, null);

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> affordabilityCardService.getAffordabilityCard(inputs));
            assertTrue(exception.getMessage().contains("annualPropertyTax"));
            verifyNoInteractions(affordabilityCalculationService);
        }
    }

}
