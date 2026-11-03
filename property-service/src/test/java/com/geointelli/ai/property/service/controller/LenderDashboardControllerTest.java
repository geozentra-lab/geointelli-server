package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.*;
import com.geointelli.ai.property.service.service.LenderDashboardMortgageContextService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LenderDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
public class LenderDashboardControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LenderDashboardMortgageContextService mortgageContextService;

    @Nested
    @DisplayName("Test Mortgage Context Card Endpoint")
    class MortgageContextTest {

        private static final String URL = "/api/dashboards/lender/mortgage-context";

        @Test
        @DisplayName("When inputs are valid, then return 200 with the OK card")
        void whenInputsAreValid_thenReturn200WithOkCard() throws Exception {
            // Arrange
            MortgageContextCardDTO card = new MortgageContextCardDTO(
                    MortgageContextCardDTO.SCOPE_SUPPLIED_INPUTS, LocalDateTime.now(), MortgageContextCardDTO.STATUS_OK, null,
                    new RateAssumptions(BigDecimal.valueOf(3.5), 30, MortgageContextCardDTO.RATE_SOURCE),
                    new MortgageCalculationResponse(new BigDecimal("320000.00"), new BigDecimal("1436.94"),
                            null, null, null, new BigDecimal("1436.94")));
            when(mortgageContextService.getMortgageContextCard(any(MortgageCalculationRequest.class))).thenReturn(card);

            // Act & Assert
            mockMvc.perform(get(URL)
                            .param("price", "400000")
                            .param("downPayment", "80000")
                            .param("annualInterestRate", "3.5")
                            .param("loanTermYears", "30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.scope").value("SUPPLIED_INPUTS"))
                    .andExpect(jsonPath("$.assumptions.rateSource").value(MortgageContextCardDTO.RATE_SOURCE))
                    .andExpect(jsonPath("$.mortgage.loanAmount").value(320000.00))
                    .andExpect(jsonPath("$.mortgage.monthlyPrincipalAndInterest").value(1436.94));
        }

        @Test
        @DisplayName("When query parameters are supplied, then they are bound to the request passed to the service")
        void whenParamsAreSupplied_thenBindThemToRequest() throws Exception {
            // Arrange
            ArgumentCaptor<MortgageCalculationRequest> captor = ArgumentCaptor.forClass(MortgageCalculationRequest.class);

            // Act
            mockMvc.perform(get(URL)
                            .param("price", "400000")
                            .param("downPayment", "80000")
                            .param("annualInterestRate", "3.5")
                            .param("loanTermYears", "30")
                            .param("monthlyHoaFees", "150"))
                    .andExpect(status().isOk());

            // Assert
            verify(mortgageContextService).getMortgageContextCard(captor.capture());
            MortgageCalculationRequest inputs = captor.getValue();
            assertEquals(new BigDecimal("400000"), inputs.price());
            assertEquals(new BigDecimal("80000"), inputs.downPayment());
            assertEquals(new BigDecimal("3.5"), inputs.annualInterestRate());
            assertEquals(30, inputs.loanTermYears());
            assertEquals(new BigDecimal("150"), inputs.monthlyHoaFees());
            assertNull(inputs.annualPropertyTax());
        }

        @Test
        @DisplayName("When no input is supplied, then return 200 with the UNAVAILABLE card")
        void whenNoInputIsSupplied_thenReturn200WithUnavailableCard() throws Exception {
            // Arrange
            MortgageContextCardDTO card = new MortgageContextCardDTO(
                    MortgageContextCardDTO.SCOPE_SUPPLIED_INPUTS, LocalDateTime.now(),
                    MortgageContextCardDTO.STATUS_UNAVAILABLE, "No mortgage inputs supplied", null, null);
            when(mortgageContextService.getMortgageContextCard(any(MortgageCalculationRequest.class))).thenReturn(card);

            // Act & Assert
            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.error").value("No mortgage inputs supplied"))
                    .andExpect(jsonPath("$.assumptions").doesNotExist())
                    .andExpect(jsonPath("$.mortgage").doesNotExist());
        }

        @Test
        @DisplayName("When the service rejects the inputs, then return 400")
        void whenServiceRejectsInputs_thenReturn400() throws Exception {
            // Arrange
            when(mortgageContextService.getMortgageContextCard(any(MortgageCalculationRequest.class)))
                    .thenThrow(new IllegalArgumentException("downPayment must not exceed price"));

            // Act & Assert
            mockMvc.perform(get(URL)
                            .param("price", "400000")
                            .param("downPayment", "500000")
                            .param("annualInterestRate", "3.5")
                            .param("loanTermYears", "30"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("downPayment must not exceed price"));
        }

        @Test
        @DisplayName("When a parameter is not a number, then return 400 without calling the service")
        void whenParamIsNotANumber_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(URL).param("price", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(mortgageContextService);
        }
    }
}
