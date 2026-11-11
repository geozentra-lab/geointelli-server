package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.InvestorOccupancyExposureCardDTO;
import com.geointelli.ai.property.service.dto.InvestorValueEquityDebtCardDTO;
import com.geointelli.ai.property.service.dto.PortfolioGeographyDTO;
import com.geointelli.ai.property.service.dto.PortfolioMetricDTO;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
import com.geointelli.ai.property.service.service.InvestorOccupancyExposureService;
import com.geointelli.ai.property.service.service.InvestorValueEquityDebtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvestorDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
public class InvestorDashboardControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    InvestorOccupancyExposureService investorOccupancyExposureService;

    @MockBean
    InvestorValueEquityDebtService investorValueEquityDebtService;

    private static final String URL = "/api/dashboards/investor/occupancy-exposure";

    private static InvestorOccupancyExposureCardDTO card(String scope, Long portfolioId, long totalHoldings) {
        Map<HoldingOccupancyStatus, Long> counts = new EnumMap<>(HoldingOccupancyStatus.class);
        for (HoldingOccupancyStatus status : HoldingOccupancyStatus.values()) {
            counts.put(status, 0L);
        }
        InvestorOccupancyExposureCardDTO card = new InvestorOccupancyExposureCardDTO();
        card.setScope(scope);
        card.setPortfolioId(portfolioId);
        card.setStatus(totalHoldings == 0
                ? InvestorOccupancyExposureCardDTO.STATUS_EMPTY : InvestorOccupancyExposureCardDTO.STATUS_OK);
        card.setGeneratedAt(Instant.parse("2026-11-10T12:00:00Z"));
        card.setAsOfDate(LocalDate.of(2026, 11, 10));
        card.setTotalHoldings(totalHoldings);
        card.setOccupancyCounts(counts);
        card.setExposureBasis("HOLDING_COUNT");
        card.setStates(List.of());
        card.setCounties(List.of());
        return card;
    }

    @Nested
    @DisplayName("Test Occupancy And Exposure Card Endpoint")
    class OccupancyExposureTest {

        @Test
        @DisplayName("When no portfolio is given, then return 200 with the card of all portfolios")
        void whenNoPortfolio_thenReturnAllPortfoliosCard() throws Exception {
            // Arrange
            InvestorOccupancyExposureCardDTO card = card(InvestorOccupancyExposureCardDTO.SCOPE_ALL_PORTFOLIOS, null, 5);
            card.getOccupancyCounts().put(HoldingOccupancyStatus.RENTED, 2L);
            card.setOccupancyCoveragePercent(new BigDecimal("80.00"));
            card.setStates(List.of(new PortfolioGeographyDTO(null, null, null, null, null, true, 1L,
                    new BigDecimal("20.00"), new BigDecimal("1.0000"))));
            when(investorOccupancyExposureService.getCard(null)).thenReturn(card);

            // Act & Assert
            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope").value("ALL_PORTFOLIOS"))
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.totalHoldings").value(5))
                    .andExpect(jsonPath("$.occupancyCounts.RENTED").value(2))
                    .andExpect(jsonPath("$.occupancyCounts.UNKNOWN").value(0))
                    .andExpect(jsonPath("$.occupancyCoveragePercent").value(80.00))
                    .andExpect(jsonPath("$.states[0].unknown").value(true))
                    .andExpect(jsonPath("$.asOfDate").value("2026-11-10"))
                    .andExpect(jsonPath("$.generatedAt").value("2026-11-10T12:00:00Z"));
            verify(investorOccupancyExposureService).getCard(null);
        }

        @Test
        @DisplayName("When a portfolio is given, then pass it to the service")
        void whenPortfolioGiven_thenPassIt() throws Exception {
            // Arrange
            when(investorOccupancyExposureService.getCard(5L))
                    .thenReturn(card(InvestorOccupancyExposureCardDTO.SCOPE_PORTFOLIO, 5L, 0));

            // Act & Assert
            mockMvc.perform(get(URL).param("portfolioId", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope").value("PORTFOLIO"))
                    .andExpect(jsonPath("$.portfolioId").value(5))
                    .andExpect(jsonPath("$.status").value("EMPTY"))
                    .andExpect(jsonPath("$.states").isEmpty());
        }

        @Test
        @DisplayName("When portfolioId is not a number, then return 400 without calling the service")
        void whenPortfolioIdNotANumber_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(URL).param("portfolioId", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(investorOccupancyExposureService);
        }

        @Test
        @DisplayName("When the service rejects portfolioId, then return 400")
        void whenServiceRejectsPortfolioId_thenReturn400() throws Exception {
            // Arrange
            when(investorOccupancyExposureService.getCard(0L))
                    .thenThrow(new IllegalArgumentException("portfolioId must be a positive number"));

            // Act & Assert
            mockMvc.perform(get(URL).param("portfolioId", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("portfolioId must be a positive number"));
        }

        @Test
        @DisplayName("When the portfolio is missing or belongs to another user, then return 404")
        void whenForeignPortfolio_thenReturn404() throws Exception {
            // Arrange
            when(investorOccupancyExposureService.getCard(9L))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));

            // Act & Assert
            mockMvc.perform(get(URL).param("portfolioId", "9"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Portfolio not found"));
        }
    }

    @Nested
    @DisplayName("Test Value, Equity And Debt Card Endpoint")
    class ValueEquityDebtTest {

        private static final String VALUE_URL = "/api/dashboards/investor/value-equity-debt";

        @Test
        @DisplayName("When no portfolio is given, then return 200 with partial totals and coverage")
        void whenNoPortfolio_thenReturnAllPortfoliosCard() throws Exception {
            // Arrange
            InvestorValueEquityDebtCardDTO card = new InvestorValueEquityDebtCardDTO();
            card.setScope(InvestorValueEquityDebtCardDTO.SCOPE_ALL_PORTFOLIOS);
            card.setStatus(InvestorValueEquityDebtCardDTO.STATUS_OK);
            card.setAsOfDate(LocalDate.of(2026, 11, 10));
            card.setCurrency("USD");
            card.setTotalHoldings(4);
            card.setValue(new PortfolioMetricDTO("PARTIAL", null, new BigDecimal("650000.00"), 3, 4));
            card.setEquity(new PortfolioMetricDTO("PARTIAL", null, new BigDecimal("200000.00"), 2, 4));
            card.setValueCoveragePercent(new BigDecimal("75.00"));
            when(investorValueEquityDebtService.getCard(null)).thenReturn(card);

            // Act & Assert
            mockMvc.perform(get(VALUE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope").value("ALL_PORTFOLIOS"))
                    .andExpect(jsonPath("$.asOfDate").value("2026-11-10"))
                    .andExpect(jsonPath("$.value.status").value("PARTIAL"))
                    .andExpect(jsonPath("$.value.total").isEmpty())
                    .andExpect(jsonPath("$.value.knownTotal").value(650000.00))
                    .andExpect(jsonPath("$.equity.coveredHoldings").value(2))
                    .andExpect(jsonPath("$.valueCoveragePercent").value(75.00));
            verify(investorValueEquityDebtService).getCard(null);
            verifyNoInteractions(investorOccupancyExposureService);
        }

        @Test
        @DisplayName("When a portfolio is given, then pass it to the service")
        void whenPortfolioGiven_thenPassIt() throws Exception {
            // Arrange
            InvestorValueEquityDebtCardDTO card = new InvestorValueEquityDebtCardDTO();
            card.setScope(InvestorValueEquityDebtCardDTO.SCOPE_PORTFOLIO);
            card.setPortfolioId(5L);
            card.setStatus(InvestorValueEquityDebtCardDTO.STATUS_EMPTY);
            when(investorValueEquityDebtService.getCard(5L)).thenReturn(card);

            // Act & Assert
            mockMvc.perform(get(VALUE_URL).param("portfolioId", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.portfolioId").value(5))
                    .andExpect(jsonPath("$.status").value("EMPTY"));
        }

        @Test
        @DisplayName("When portfolioId is not a number, then return 400 without calling the service")
        void whenPortfolioIdNotANumber_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(VALUE_URL).param("portfolioId", "abc"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(investorValueEquityDebtService);
        }

        @Test
        @DisplayName("When the service rejects portfolioId, then return 400")
        void whenServiceRejectsPortfolioId_thenReturn400() throws Exception {
            // Arrange
            when(investorValueEquityDebtService.getCard(-1L))
                    .thenThrow(new IllegalArgumentException("portfolioId must be a positive number"));

            // Act & Assert
            mockMvc.perform(get(VALUE_URL).param("portfolioId", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("portfolioId must be a positive number"));
        }

        @Test
        @DisplayName("When the portfolio is missing or belongs to another user, then return 404")
        void whenForeignPortfolio_thenReturn404() throws Exception {
            // Arrange
            when(investorValueEquityDebtService.getCard(9L))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));

            // Act & Assert
            mockMvc.perform(get(VALUE_URL).param("portfolioId", "9"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Portfolio not found"));
        }
    }
}
