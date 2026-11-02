package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;
import com.geointelli.ai.property.service.dto.MortgageCalculationResponse;
import com.geointelli.ai.property.service.dto.MortgageContextCardDTO;
import com.geointelli.ai.property.service.service.MortgageCalculationService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LenderDashboardMortgageContextServiceImplTest {

    MortgageCalculationService mortgageCalculationService = mock(MortgageCalculationService.class);

    LenderDashboardMortgageContextServiceImpl mortgageContextService = new LenderDashboardMortgageContextServiceImpl(
            mortgageCalculationService, Validation.buildDefaultValidatorFactory().getValidator());

    private static MortgageCalculationRequest validInputs() {
        return new MortgageCalculationRequest(
                BigDecimal.valueOf(400000), // price
                BigDecimal.valueOf(80000),  // downPayment
                BigDecimal.valueOf(3.5),    // annualInterestRate
                30,                         // loanTermYears
                null, null, null            // tax, insurance, HOA
        );
    }

    private static MortgageCalculationResponse mortgageResult() {
        return new MortgageCalculationResponse(new BigDecimal("320000.00"), new BigDecimal("1436.94"),
                null, null, null, new BigDecimal("1436.94"));
    }

    @Nested
    @DisplayName("GetMortgageContextCard Tests")
    class GetMortgageContextCard {

        @Test
        @DisplayName("When inputs are valid, then return OK with the reused mortgage result and the rate assumptions")
        void whenInputsAreValid_thenReturnOkCard() {
            // Arrange
            MortgageCalculationRequest inputs = validInputs();
            MortgageCalculationResponse mortgage = mortgageResult();
            when(mortgageCalculationService.calculateMortgage(inputs)).thenReturn(mortgage);

            // Act
            MortgageContextCardDTO card = mortgageContextService.getMortgageContextCard(inputs);

            // Assert
            assertEquals(MortgageContextCardDTO.STATUS_OK, card.status());
            assertEquals(MortgageContextCardDTO.SCOPE_SUPPLIED_INPUTS, card.scope());
            assertNull(card.error());
            assertSame(mortgage, card.mortgage());
            assertEquals(BigDecimal.valueOf(3.5), card.assumptions().annualInterestRate());
            assertEquals(30, card.assumptions().loanTermYears());
            assertEquals(MortgageContextCardDTO.RATE_SOURCE, card.assumptions().rateSource());
            assertNotNull(card.generatedAt());
            verify(mortgageCalculationService).calculateMortgage(inputs);
        }

        @Test
        @DisplayName("When no input is supplied, then return UNAVAILABLE without calling the calculator")
        void whenNoInputIsSupplied_thenReturnUnavailableCard() {
            // Arrange
            MortgageCalculationRequest inputs = new MortgageCalculationRequest(null, null, null, null, null, null, null);

            // Act
            MortgageContextCardDTO card = mortgageContextService.getMortgageContextCard(inputs);

            // Assert
            assertEquals(MortgageContextCardDTO.STATUS_UNAVAILABLE, card.status());
            assertEquals(LenderDashboardMortgageContextServiceImpl.NO_INPUT_ERROR, card.error());
            assertNull(card.assumptions());
            assertNull(card.mortgage());
            assertNotNull(card.generatedAt());
            verifyNoInteractions(mortgageCalculationService);
        }

        @Test
        @DisplayName("When inputs are null, then return UNAVAILABLE")
        void whenInputsAreNull_thenReturnUnavailableCard() {
            // Act
            MortgageContextCardDTO card = mortgageContextService.getMortgageContextCard(null);

            // Assert
            assertEquals(MortgageContextCardDTO.STATUS_UNAVAILABLE, card.status());
            verifyNoInteractions(mortgageCalculationService);
        }

        @Test
        @DisplayName("When only an optional cost is supplied, then the required fields are reported")
        void whenOnlyOptionalCostIsSupplied_thenThrowIllegalArgumentException() {
            // Arrange
            MortgageCalculationRequest inputs = new MortgageCalculationRequest(
                    null, null, null, null, BigDecimal.valueOf(4800), null, null);

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> mortgageContextService.getMortgageContextCard(inputs));
            assertTrue(exception.getMessage().contains("price"));
            verifyNoInteractions(mortgageCalculationService);
        }

        @Test
        @DisplayName("When inputs are partial, then throw IllegalArgumentException naming every missing field")
        void whenInputsArePartial_thenThrowIllegalArgumentException() {
            // Arrange
            MortgageCalculationRequest inputs = new MortgageCalculationRequest(
                    BigDecimal.valueOf(400000), null, null, null, null, null, null);

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> mortgageContextService.getMortgageContextCard(inputs));
            assertTrue(exception.getMessage().contains("downPayment"));
            assertTrue(exception.getMessage().contains("annualInterestRate"));
            assertTrue(exception.getMessage().contains("loanTermYears"));
            verifyNoInteractions(mortgageCalculationService);
        }

        @Test
        @DisplayName("When the rate is above 100, then throw IllegalArgumentException")
        void whenRateIsAbove100_thenThrowIllegalArgumentException() {
            // Arrange
            MortgageCalculationRequest inputs = new MortgageCalculationRequest(
                    BigDecimal.valueOf(400000), BigDecimal.valueOf(80000), BigDecimal.valueOf(150), 30, null, null, null);

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> mortgageContextService.getMortgageContextCard(inputs));
            assertTrue(exception.getMessage().contains("annualInterestRate"));
            verifyNoInteractions(mortgageCalculationService);
        }

        @Test
        @DisplayName("When the calculator rejects the inputs, then its exception is propagated")
        void whenCalculatorRejectsInputs_thenPropagateException() {
            // Arrange
            MortgageCalculationRequest inputs = new MortgageCalculationRequest(
                    BigDecimal.valueOf(400000), BigDecimal.valueOf(500000), BigDecimal.valueOf(3.5), 30, null, null, null);
            when(mortgageCalculationService.calculateMortgage(inputs))
                    .thenThrow(new IllegalArgumentException("downPayment must not exceed price"));

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> mortgageContextService.getMortgageContextCard(inputs));
            assertEquals("downPayment must not exceed price", exception.getMessage());
        }
    }
}
