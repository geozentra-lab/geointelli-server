package com.geointelli.ai.property.service.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.InvestmentAnalysisRequest;
import com.geointelli.ai.property.service.dto.InvestmentAnalysisResponse;
import com.geointelli.ai.property.service.dto.ScenarioCalculationDTO;
import com.geointelli.ai.property.service.dto.ScenarioDTO;
import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.entity.ScenarioType;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.service.InvestmentAnalysisService;
import com.geointelli.ai.property.service.service.ScenarioService;
import com.geointelli.ai.property.service.service.ValuationService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ScenarioCalculationServiceImplTest {

    ScenarioService scenarioService = mock(ScenarioService.class);
    InvestmentAnalysisService investmentAnalysisService = mock(InvestmentAnalysisService.class);
    ValuationService valuationService = mock(ValuationService.class);
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    ScenarioCalculationServiceImpl calculationService = new ScenarioCalculationServiceImpl(
            scenarioService, investmentAnalysisService, valuationService, new ObjectMapper(), validator);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Long PROPERTY_ID = 100L;
    private static final String ASSUMPTIONS = """
            {"operating": {"monthlyRent": 2500, "annualPropertyTax": 6000},
             "financing": {"purchasePrice": 400000, "downPayment": 80000, "annualInterestRate": 6.5,
                           "loanTermYears": 30}}""";

    private final InvestmentAnalysisResponse result = new InvestmentAnalysisResponse(null, null,
            new BigDecimal("7.50"), null, List.of());

    private static JsonNode json(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static ScenarioDTO scenario(Long propertyId, String assumptions, int inputVersion) {
        return new ScenarioDTO(5L, "Brickell condo", ScenarioType.INVESTMENT, inputVersion, propertyId,
                json(assumptions), LocalDateTime.of(2026, 8, 1, 10, 0), LocalDateTime.of(2026, 8, 1, 10, 0));
    }

    private static ValuationResponse valuation(String status, String value) {
        ValuationResponse.Estimate estimate = value == null ? null
                : new ValuationResponse.Estimate(new BigDecimal(value), null, null, "high");
        ValuationResponse.Method method = new ValuationResponse.Method("avm", "v3", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 10, 31), null);
        return new ValuationResponse(status, List.of(), estimate, null, null, null, method, null, null);
    }

    @BeforeEach
    void setUp() {
        when(investmentAnalysisService.analyze(any())).thenReturn(result);
    }

    @Nested
    @DisplayName("Test Calculate With Saved Assumptions")
    class CalculateTest {

        @Test
        @DisplayName("When the scenario is valid, then the shared service calculates the saved inputs and a new value is disclosed")
        void whenValid_thenSharedServiceCalculatesSavedInputs() {
            // Arrange
            when(scenarioService.get(5L)).thenReturn(scenario(PROPERTY_ID, ASSUMPTIONS, 1));
            when(valuationService.getValuationWithoutComparables(PROPERTY_ID))
                    .thenReturn(valuation("supported", "430000.00"));

            // Act
            ScenarioCalculationDTO dto = calculationService.calculate(5L);

            // Assert: the saved assumptions are the inputs of the shared calculation
            ArgumentCaptor<InvestmentAnalysisRequest> request = ArgumentCaptor.forClass(InvestmentAnalysisRequest.class);
            verify(investmentAnalysisService).analyze(request.capture());
            assertEquals(0, new BigDecimal("2500").compareTo(request.getValue().operating().monthlyRent()));
            assertEquals(0, new BigDecimal("400000").compareTo(request.getValue().financing().purchasePrice()));
            assertSame(result, dto.result());

            assertEquals(5L, dto.scenarioId());
            assertEquals("Brickell condo", dto.scenarioName());
            assertEquals(json(ASSUMPTIONS), dto.savedAssumptions());
            assertEquals(ScenarioCalculationDTO.FACTS_AVAILABLE, dto.currentFacts().status());
            assertEquals(0, new BigDecimal("430000.00").compareTo(dto.currentFacts().estimatedValue()));
            assertEquals("high", dto.currentFacts().confidence());
            assertEquals(LocalDate.of(2026, 11, 1), dto.currentFacts().valuationDate());
            assertEquals(1, dto.inputChanges().size());
            assertEquals("financing.purchasePrice", dto.inputChanges().get(0).field());
            assertEquals(0, new BigDecimal("400000").compareTo(dto.inputChanges().get(0).savedValue()));
            assertEquals(0, new BigDecimal("430000.00").compareTo(dto.inputChanges().get(0).currentValue()));
            assertEquals(ScenarioCalculationDTO.NOTE, dto.note());
        }

        @Test
        @DisplayName("When the saved price equals the current value, then no change is reported")
        void whenSamePrice_thenNoChange() {
            // Arrange
            when(scenarioService.get(5L)).thenReturn(scenario(PROPERTY_ID, ASSUMPTIONS, 1));
            when(valuationService.getValuationWithoutComparables(PROPERTY_ID))
                    .thenReturn(valuation("supported", "400000.00"));

            // Act & Assert
            assertTrue(calculationService.calculate(5L).inputChanges().isEmpty());
        }

        @Test
        @DisplayName("When there is no financing, then the purchase price is not compared")
        void whenNoFinancing_thenNoPriceComparison() {
            // Arrange
            when(scenarioService.get(5L)).thenReturn(scenario(PROPERTY_ID, "{\"operating\": {\"monthlyRent\": 2500}}", 1));
            when(valuationService.getValuationWithoutComparables(PROPERTY_ID))
                    .thenReturn(valuation("supported", "430000.00"));

            // Act
            ScenarioCalculationDTO dto = calculationService.calculate(5L);

            // Assert
            assertEquals(ScenarioCalculationDTO.FACTS_AVAILABLE, dto.currentFacts().status());
            assertTrue(dto.inputChanges().isEmpty());
        }
    }

    @Nested
    @DisplayName("Test Current Facts Unavailable")
    class CurrentFactsTest {

        @Test
        @DisplayName("When no property is linked, then the calculation runs and no valuation is read")
        void whenNoLinkedProperty_thenNoValuationRead() {
            // Arrange
            when(scenarioService.get(5L)).thenReturn(scenario(null, ASSUMPTIONS, 1));

            // Act
            ScenarioCalculationDTO dto = calculationService.calculate(5L);

            // Assert
            assertEquals(ScenarioCalculationDTO.NO_LINKED_PROPERTY, dto.currentFacts().status());
            assertTrue(dto.inputChanges().isEmpty());
            assertSame(result, dto.result());
            verifyNoInteractions(valuationService);
        }

        @Test
        @DisplayName("When the linked property was deleted, then the calculation runs and the missing property is disclosed")
        void whenPropertyDeleted_thenDisclosed() {
            // Arrange
            when(scenarioService.get(5L)).thenReturn(scenario(PROPERTY_ID, ASSUMPTIONS, 1));
            when(valuationService.getValuationWithoutComparables(PROPERTY_ID))
                    .thenThrow(new PropertyNotFoundException("Property not found: 100"));

            // Act
            ScenarioCalculationDTO dto = calculationService.calculate(5L);

            // Assert
            assertEquals(ScenarioCalculationDTO.PROPERTY_NOT_FOUND, dto.currentFacts().status());
            assertNull(dto.currentFacts().estimatedValue());
            assertEquals("propertyId", dto.inputChanges().get(0).field());
            assertSame(result, dto.result());
        }

        @Test
        @DisplayName("When the property has no valuation, then the purchase price is reported as not comparable")
        void whenValuationUnavailable_thenPriceNotComparable() {
            // Arrange
            when(scenarioService.get(5L)).thenReturn(scenario(PROPERTY_ID, ASSUMPTIONS, 1));
            when(valuationService.getValuationWithoutComparables(PROPERTY_ID))
                    .thenReturn(valuation("unsupported", null));

            // Act
            ScenarioCalculationDTO dto = calculationService.calculate(5L);

            // Assert
            assertEquals(ScenarioCalculationDTO.VALUATION_UNAVAILABLE, dto.currentFacts().status());
            assertEquals("unsupported", dto.currentFacts().valuationStatus());
            assertEquals(1, dto.inputChanges().size());
            assertEquals("financing.purchasePrice", dto.inputChanges().get(0).field());
            assertNull(dto.inputChanges().get(0).currentValue());
        }
    }

    @Nested
    @DisplayName("Test Invalid Scenario")
    class InvalidTest {

        @Test
        @DisplayName("When a saved value breaks a calculation rule, then throw IllegalArgumentException without calculating")
        void whenRuleBroken_thenThrow() {
            // Arrange
            when(scenarioService.get(5L)).thenReturn(scenario(PROPERTY_ID, "{\"operating\": {\"monthlyRent\": -100}}", 1));

            // Act & Assert
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> calculationService.calculate(5L));
            assertTrue(error.getMessage().contains("operating.monthlyRent"));
            verifyNoInteractions(investmentAnalysisService, valuationService);
        }

        @Test
        @DisplayName("When the operating section is missing, then throw IllegalArgumentException")
        void whenOperatingMissing_thenThrow() {
            // Arrange
            when(scenarioService.get(5L)).thenReturn(scenario(PROPERTY_ID, "{\"financing\": {\"purchasePrice\": 400000}}", 1));

            // Act & Assert
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> calculationService.calculate(5L));
            assertTrue(error.getMessage().contains("operating"));
            verifyNoInteractions(investmentAnalysisService);
        }

        @Test
        @DisplayName("When a saved value has the wrong type, then throw IllegalArgumentException")
        void whenWrongType_thenThrow() {
            // Arrange
            when(scenarioService.get(5L)).thenReturn(scenario(PROPERTY_ID, "{\"operating\": {\"monthlyRent\": \"abc\"}}", 1));

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> calculationService.calculate(5L));
            verifyNoInteractions(investmentAnalysisService);
        }

        @Test
        @DisplayName("When the assumptions version is not supported, then throw IllegalArgumentException")
        void whenUnsupportedVersion_thenThrow() {
            // Arrange
            when(scenarioService.get(5L)).thenReturn(scenario(PROPERTY_ID, ASSUMPTIONS, 2));

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> calculationService.calculate(5L));
            verifyNoInteractions(investmentAnalysisService);
        }

        @Test
        @DisplayName("When the scenario is missing or foreign, then the 404 is kept and nothing is calculated")
        void whenMissingOrForeign_thenThrow404() {
            // Arrange
            when(scenarioService.get(5L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Scenario not found"));

            // Act & Assert
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> calculationService.calculate(5L));
            assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
            verifyNoInteractions(investmentAnalysisService, valuationService);
        }
    }
}
