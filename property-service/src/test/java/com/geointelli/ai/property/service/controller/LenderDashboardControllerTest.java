package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.*;
import com.geointelli.ai.property.service.entity.LenderLoanType;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.LenderApprovedFundedService;
import com.geointelli.ai.property.service.service.LenderDashboardMortgageContextService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    @MockBean
    LenderApprovedFundedService approvedFundedService;

    @MockBean
    CurrentUserService currentUserService;

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

    @Nested
    @DisplayName("Test Approved, Funded And Loan-Mix Card Endpoint")
    class ApprovedFundedTest {

        private static final String URL = "/api/dashboards/lender/approved-funded";
        private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
        private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

        private void givenCurrentUser() {
            User user = new User();
            user.setId(7L);
            when(currentUserService.getCurrentUserEntity()).thenReturn(user);
        }

        @Test
        @DisplayName("When the period is valid, then return 200 with approved, funded and the loan mix")
        void whenValid_thenReturnCard() throws Exception {
            // Arrange
            givenCurrentUser();
            LenderApprovedFundedCardDTO card = new LenderApprovedFundedCardDTO(42L, "USD",
                    LenderApprovedFundedCardDTO.STATUS_OK, null, Instant.parse("2026-10-15T12:00:00Z"),
                    new LenderApprovedFundedCardDTO.Period(OCT_1, OCT_31, "UTC"),
                    new LenderApprovedFundedCardDTO.AmountTotal(12, new BigDecimal("3600000.00")),
                    new LenderApprovedFundedCardDTO.AmountTotal(10, new BigDecimal("3000000.00")),
                    List.of(new LenderApprovedFundedCardDTO.LoanMixEntry(LenderLoanType.FHA, 2, new BigDecimal("540000.00")),
                            new LenderApprovedFundedCardDTO.LoanMixEntry(LenderLoanType.UNKNOWN, 1, new BigDecimal("250000.00"))),
                    "note");
            when(approvedFundedService.getCard(7L, 42L, OCT_1, OCT_31)).thenReturn(card);

            // Act & Assert
            mockMvc.perform(get(URL).param("organizationId", "42")
                            .param("fromDate", "2026-10-01").param("toDate", "2026-10-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.organizationId").value(42))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.period.fromDate").value("2026-10-01"))
                    .andExpect(jsonPath("$.period.timeZone").value("UTC"))
                    .andExpect(jsonPath("$.approved.count").value(12))
                    .andExpect(jsonPath("$.funded.totalAmount").value(3000000.00))
                    .andExpect(jsonPath("$.fundedLoanMix[1].loanType").value("UNKNOWN"))
                    .andExpect(jsonPath("$.generatedAt").value("2026-10-15T12:00:00Z"));
        }

        @Test
        @DisplayName("When no dates are given, then pass null dates to the service")
        void whenNoDates_thenPassNull() throws Exception {
            // Arrange
            givenCurrentUser();

            // Act & Assert
            mockMvc.perform(get(URL).param("organizationId", "42")).andExpect(status().isOk());
            verify(approvedFundedService).getCard(7L, 42L, null, null);
        }

        @Test
        @DisplayName("When organizationId is missing, then return 400 without calling the service")
        void whenOrganizationMissing_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(URL)).andExpect(status().isBadRequest());
            verifyNoInteractions(approvedFundedService);
        }

        @Test
        @DisplayName("When a date is not yyyy-MM-dd, then return 400 without calling the service")
        void whenBadDate_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(URL).param("organizationId", "42").param("fromDate", "01/10/2026")
                            .param("toDate", "2026-10-31"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(approvedFundedService);
        }

        @Test
        @DisplayName("When the service rejects the period, then return 400")
        void whenServiceRejectsPeriod_thenReturn400() throws Exception {
            // Arrange
            givenCurrentUser();
            when(approvedFundedService.getCard(7L, 42L, OCT_31, OCT_1))
                    .thenThrow(new IllegalArgumentException("fromDate must not be after toDate"));

            // Act & Assert
            mockMvc.perform(get(URL).param("organizationId", "42")
                            .param("fromDate", "2026-10-31").param("toDate", "2026-10-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("fromDate must not be after toDate"));
        }

        @Test
        @DisplayName("When the user is not a member, then return 404")
        void whenNotMember_thenReturn404() throws Exception {
            // Arrange
            givenCurrentUser();
            when(approvedFundedService.getCard(7L, 9L, null, null))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

            // Act & Assert
            mockMvc.perform(get(URL).param("organizationId", "9"))
                    .andExpect(status().isNotFound());
        }
    }
}
