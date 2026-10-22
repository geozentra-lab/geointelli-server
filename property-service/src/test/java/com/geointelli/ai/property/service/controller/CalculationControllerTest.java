package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;
import com.geointelli.ai.property.service.dto.MortgageCalculationResponse;
import com.geointelli.ai.property.service.service.MortgageCalculationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CalculationController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CalculationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    MortgageCalculationService mortgageCalculationService;

    @Nested
    @DisplayName("Test Calculate Mortgage Endpoint")
    class CalculateMortgageTest {

        private static final String URL = "/api/calculations/mortgage";

        private static final String VALID_BODY = """
        {
          "price": 400000,
          "downPayment": 80000,
          "annualInterestRate": 3.5,
          "loanTermYears": 30,
          "annualPropertyTax": 3000,
          "annualHomeInsurance": 1200,
          "monthlyHoaFees": 100
        }
        """;

        @Test
        void whenRequestIsValid_thenReturn200() throws Exception {

            // Arrange
            MortgageCalculationResponse response = new MortgageCalculationResponse(
                    new BigDecimal("320000.00"), new BigDecimal("1436.94"),
                    new BigDecimal("250.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("1886.94"));

            when(mortgageCalculationService.calculateMortgage(any(MortgageCalculationRequest.class)))
                    .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loanAmount").value(320000.00))
                    .andExpect(jsonPath("$.monthlyPrincipalAndInterest").value(1436.94))
                    .andExpect(jsonPath("$.totalMonthlyKnownCosts").value(1886.94));
            verify(mortgageCalculationService).calculateMortgage(any(MortgageCalculationRequest.class));
        }

        @Test
        @DisplayName("Test when down payment is greater than price")
        void whenDownPaymentIsGreaterThanPrice_thenReturn400() throws Exception {
            // Arrange
            String invalidBody = """
            {
              "price": 400000,
              "downPayment": 500000,
              "annualInterestRate": 3.5,
              "loanTermYears": 30,
              "annualPropertyTax": 3000,
              "annualHomeInsurance": 1200,
              "monthlyHoaFees": 100
            }
            """;
            when(mortgageCalculationService.calculateMortgage(any(MortgageCalculationRequest.class)))
                    .thenThrow(new IllegalArgumentException("downPayment must not exceed price"));

            // Act & Assert
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("downPayment must not exceed price"));
        }

        @Test
        @DisplayName("When ownership costs are not supplied, then they are returned as null")
        void whenCostsMissing_thenReturnedAsNull() throws Exception {
            // Arrange
            MortgageCalculationResponse response = new MortgageCalculationResponse(
                    new BigDecimal("320000.00"), new BigDecimal("1436.94"),
                    null, null, null, new BigDecimal("1436.94"));
            when(mortgageCalculationService.calculateMortgage(any(MortgageCalculationRequest.class)))
                    .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                    {"price": 400000, "downPayment": 80000, "annualInterestRate": 3.5, "loanTermYears": 30}
                    """))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                    {"monthlyPropertyTax": null, "monthlyInsurance": null, "monthlyHoaFees": null,
                     "totalMonthlyKnownCosts": 1436.94}
                    """));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                // missing required field
                """
                {"downPayment": 80000, "annualInterestRate": 3.5, "loanTermYears": 30}""",
                // price = 0
                """
                {"price": 0, "downPayment": 0, "annualInterestRate": 3.5, "loanTermYears": 30}""",
                // negative down payment
                """
                {"price": 400000, "downPayment": -1, "annualInterestRate": 3.5, "loanTermYears": 30}""",
                // rate below 0 / above 100
                """
                {"price": 400000, "downPayment": 80000, "annualInterestRate": -1, "loanTermYears": 30}""",
                """
                {"price": 400000, "downPayment": 80000, "annualInterestRate": 101, "loanTermYears": 30}""",
                // term 0 / 51
                """
                {"price": 400000, "downPayment": 80000, "annualInterestRate": 3.5, "loanTermYears": 0}""",
                """
                {"price": 400000, "downPayment": 80000, "annualInterestRate": 3.5, "loanTermYears": 51}""",
                // negative optional cost
                """
                {"price": 400000, "downPayment": 80000, "annualInterestRate": 3.5, "loanTermYears": 30, "annualPropertyTax": -5}""",
                // empty object
                "{}",
                // malformed JSON / wrong type
                """
                {"price": "abc", "downPayment": 80000, "annualInterestRate": 3.5, "loanTermYears": 30}""",
                "{ not json"
        })
        void whenBodyIsInvalid_thenReturn400(String body) throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(mortgageCalculationService);
        }

    }

}
