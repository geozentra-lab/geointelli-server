package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.AffordabilityAssumptions;
import com.geointelli.ai.property.service.dto.AffordabilityCalculationRequest;
import com.geointelli.ai.property.service.dto.AffordabilityCalculationResponse;
import com.geointelli.ai.property.service.dto.AffordabilityCardDTO;
import com.geointelli.ai.property.service.service.UserDashboardAffordabilityService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserDashboardAffordabilityController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserDashboardAffordabilityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UserDashboardAffordabilityService affordabilityCardService;

    private static AffordabilityCardDTO card(String status, String error, List<String> missingInputs,
                                             AffordabilityCalculationResponse affordability) {
        AffordabilityCardDTO card = new AffordabilityCardDTO();
        card.setScope(AffordabilityCardDTO.SCOPE_CURRENT_USER);
        card.setGeneratedAt(LocalDateTime.now());
        card.setStatus(status);
        card.setError(error);
        card.setMissingInputs(missingInputs);
        card.setAffordability(affordability);
        return card;
    }

    @Nested
    @DisplayName("Test Affordability Card Endpoint")
    class AffordabilityCardTest {

        private static final String URL = "/api/dashboards/user/affordability";

        @Test
        @DisplayName("When required inputs are supplied, then return 200 with the OK card")
        void whenRequiredInputsAreSupplied_thenReturn200WithOkCard() throws Exception {
            // Arrange
            AffordabilityCalculationResponse affordability = new AffordabilityCalculationResponse(
                    new BigDecimal("6000.00"), new BigDecimal("1660.00"), new BigDecimal("1660.00"),
                    new BigDecimal("262629.96"), new BigDecimal("312629.96"),
                    new AffordabilityAssumptions(BigDecimal.valueOf(28), BigDecimal.valueOf(36),
                            BigDecimal.valueOf(6.5), 30, "Supplied by user, not a market rate"),
                    List.of(), "This is an affordability estimate, not a loan approval.");
            when(affordabilityCardService.getAffordabilityCard(any(AffordabilityCalculationRequest.class)))
                    .thenReturn(card(AffordabilityCardDTO.STATUS_OK, null, List.of(), affordability));

            // Act & Assert
            mockMvc.perform(get(URL)
                            .param("annualGrossIncome", "72000")
                            .param("monthlyDebtPayments", "500")
                            .param("downPayment", "50000")
                            .param("annualInterestRate", "6.5")
                            .param("loanTermYears", "30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.scope").value("CURRENT_USER"))
                    .andExpect(jsonPath("$.missingInputs").isEmpty())
                    .andExpect(jsonPath("$.affordability.maxLoanAmount").value(262629.96))
                    .andExpect(jsonPath("$.affordability.assumptions.frontEndRatioPercent").value(28))
                    .andExpect(jsonPath("$.affordability.disclaimer").value("This is an affordability estimate, not a loan approval."));
        }

        @Test
        @DisplayName("When query parameters are supplied, then they are bound to the request passed to the service")
        void whenParamsAreSupplied_thenBindThemToRequest() throws Exception {
            // Arrange
            ArgumentCaptor<AffordabilityCalculationRequest> captor =
                    ArgumentCaptor.forClass(AffordabilityCalculationRequest.class);

            // Act
            mockMvc.perform(get(URL)
                            .param("annualGrossIncome", "72000")
                            .param("monthlyDebtPayments", "500")
                            .param("downPayment", "50000")
                            .param("annualInterestRate", "6.5")
                            .param("loanTermYears", "30")
                            .param("monthlyHoaFees", "100"))
                    .andExpect(status().isOk());

            // Assert
            verify(affordabilityCardService).getAffordabilityCard(captor.capture());
            AffordabilityCalculationRequest inputs = captor.getValue();
            assertEquals(new BigDecimal("72000"), inputs.annualGrossIncome());
            assertEquals(new BigDecimal("500"), inputs.monthlyDebtPayments());
            assertEquals(new BigDecimal("50000"), inputs.downPayment());
            assertEquals(new BigDecimal("6.5"), inputs.annualInterestRate());
            assertEquals(30, inputs.loanTermYears());
            assertEquals(new BigDecimal("100"), inputs.monthlyHoaFees());
            assertNull(inputs.annualPropertyTax());
        }

        @Test
        @DisplayName("When no input is supplied, then return 200 with the INCOMPLETE card")
        void whenNoInputIsSupplied_thenReturn200WithIncompleteCard() throws Exception {
            // Arrange
            List<String> missing = List.of("annualGrossIncome", "monthlyDebtPayments", "downPayment",
                    "annualInterestRate", "loanTermYears");
            when(affordabilityCardService.getAffordabilityCard(any(AffordabilityCalculationRequest.class)))
                    .thenReturn(card(AffordabilityCardDTO.STATUS_INCOMPLETE,
                            "Required financial inputs not supplied", missing, null));

            // Act & Assert
            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                    .andExpect(jsonPath("$.error").value("Required financial inputs not supplied"))
                    .andExpect(jsonPath("$.missingInputs.length()").value(5))
                    .andExpect(jsonPath("$.missingInputs[0]").value("annualGrossIncome"))
                    .andExpect(jsonPath("$.affordability").doesNotExist());
        }

        @Test
        @DisplayName("When the service rejects the inputs, then return 400")
        void whenServiceRejectsInputs_thenReturn400() throws Exception {
            // Arrange
            when(affordabilityCardService.getAffordabilityCard(any(AffordabilityCalculationRequest.class)))
                    .thenThrow(new IllegalArgumentException("annualInterestRate: must be less than or equal to 100"));

            // Act & Assert
            mockMvc.perform(get(URL)
                            .param("annualGrossIncome", "72000")
                            .param("monthlyDebtPayments", "500")
                            .param("downPayment", "50000")
                            .param("annualInterestRate", "150")
                            .param("loanTermYears", "30"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("annualInterestRate: must be less than or equal to 100"));
        }

        @Test
        @DisplayName("When a parameter is not a number, then return 400 without calling the service")
        void whenParamIsNotANumber_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(URL).param("annualGrossIncome", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(affordabilityCardService);
        }
    }
}
