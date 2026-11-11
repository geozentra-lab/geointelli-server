package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestorValueEquityDebtCardDTO;
import com.geointelli.ai.property.service.dto.PortfolioMetricDTO;
import com.geointelli.ai.property.service.dto.PortfolioValuationDTO;
import com.geointelli.ai.property.service.entity.HoldingFinancingType;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.PortfolioHoldingSnapshot;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.repository.PortfolioSummaryRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PortfolioValuationService;
import com.geointelli.ai.property.service.service.PortfolioValueSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class InvestorValueEquityDebtServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    CurrentUserService currentUserService = mock(CurrentUserService.class);
    PortfolioRepository portfolioRepository = mock(PortfolioRepository.class);
    PortfolioSummaryRepository portfolioSummaryRepository = mock(PortfolioSummaryRepository.class);
    PortfolioValuationService portfolioValuationService = mock(PortfolioValuationService.class);
    // The real SCRUM-74 value service, spied to check that it is reused once per portfolio
    PortfolioValueSummaryService portfolioValueSummaryService =
            spy(new PortfolioValueSummaryServiceImpl(portfolioSummaryRepository, portfolioValuationService));

    InvestorValueEquityDebtServiceImpl investorValueEquityDebtService = new InvestorValueEquityDebtServiceImpl(
            currentUserService, portfolioRepository, portfolioValueSummaryService,
            Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC));

    private static PortfolioHoldingSnapshot holding(long id, String sharePercent, HoldingFinancingType financing,
                                                    String balance, LocalDate balanceDate) {
        return new PortfolioHoldingSnapshot(id, id, new BigDecimal(sharePercent), financing,
                balance == null ? null : new BigDecimal(balance), balanceDate, null, null, null, null, null, null,
                LocalDateTime.of(2026, 11, (int) id, 9, 0));
    }

    private static PortfolioValuationDTO valuation(String amount, LocalDate date, String source) {
        return new PortfolioValuationDTO(new BigDecimal(amount), "USD", date, source);
    }

    private static Portfolio portfolio(long id) {
        Portfolio portfolio = new Portfolio();
        portfolio.setId(id);
        return portfolio;
    }

    /**
     * Ali: "Florida" has A (300 000, loan 200 000), B (50 % of 200 000, cash) and C (no value, loan 150 000);
     * "Texas" has D (250 000, unknown loan balance).
     */
    private void givenTwoPortfolios() {
        when(portfolioRepository.findByUser_Id(eq(USER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(portfolio(1L), portfolio(2L))));
        when(portfolioSummaryRepository.holdings(1L, USER_ID)).thenReturn(List.of(
                holding(1, "100", HoldingFinancingType.FINANCED, "200000", LocalDate.of(2026, 10, 1)),
                holding(2, "50", HoldingFinancingType.CASH, null, null),
                holding(3, "100", HoldingFinancingType.FINANCED, "150000", LocalDate.of(2026, 6, 30))));
        when(portfolioSummaryRepository.holdings(2L, USER_ID)).thenReturn(List.of(
                holding(4, "100", HoldingFinancingType.FINANCED, null, null)));
        Map<Long, PortfolioValuationDTO> values = Map.of(
                1L, valuation("300000", LocalDate.of(2026, 9, 1), "AVM"),
                2L, valuation("200000", LocalDate.of(2026, 8, 15), "AVM"),
                4L, valuation("250000", LocalDate.of(2026, 10, 20), "APPRAISAL"));
        when(portfolioValuationService.latestValues(any(), eq(TODAY))).thenAnswer(invocation -> {
            Set<Long> propertyIds = invocation.getArgument(0);
            Map<Long, PortfolioValuationDTO> found = new HashMap<>();
            propertyIds.stream().filter(values::containsKey).forEach(id -> found.put(id, values.get(id)));
            return found;
        });
    }

    private static void assertMetric(PortfolioMetricDTO metric, String status, String total, String knownTotal,
                                     long covered, long totalHoldings) {
        assertEquals(status, metric.status());
        assertEquals(total == null ? null : new BigDecimal(total), metric.total());
        assertEquals(new BigDecimal(knownTotal), metric.knownTotal());
        assertEquals(covered, metric.coveredHoldings());
        assertEquals(totalHoldings, metric.totalHoldings());
    }

    @BeforeEach
    void currentUser() {
        User user = new User();
        user.setId(USER_ID);
        when(currentUserService.getCurrentUserEntity()).thenReturn(user);
    }

    @Nested
    @DisplayName("GetCard Tests")
    class GetCard {

        @Test
        @DisplayName("When no portfolio is given, then add up every portfolio's value, debt and equity with coverage")
        void whenAllPortfolios_thenAddUpAmountsWithCoverage() {
            // Arrange
            givenTwoPortfolios();

            // Act
            InvestorValueEquityDebtCardDTO card = investorValueEquityDebtService.getCard(null);

            // Assert
            assertEquals(InvestorValueEquityDebtCardDTO.SCOPE_ALL_PORTFOLIOS, card.getScope());
            assertNull(card.getPortfolioId());
            assertEquals(2, card.getPortfolioCount());
            assertEquals(InvestorValueEquityDebtCardDTO.STATUS_OK, card.getStatus());
            assertNull(card.getError());
            assertEquals(TODAY, card.getAsOfDate());
            assertEquals("USD", card.getCurrency());
            assertEquals(4, card.getTotalHoldings());
            assertMetric(card.getValue(), "PARTIAL", null, "650000.00", 3, 4);
            assertMetric(card.getDebt(), "PARTIAL", null, "350000.00", 3, 4);
            assertMetric(card.getEquity(), "PARTIAL", null, "200000.00", 2, 4);
            assertEquals(new BigDecimal("75.00"), card.getValueCoveragePercent());
            assertEquals(new BigDecimal("75.00"), card.getDebtCoveragePercent());
            assertEquals(new BigDecimal("50.00"), card.getEquityCoveragePercent());
            verify(portfolioValueSummaryService).summarize(1L, USER_ID, TODAY);
            verify(portfolioValueSummaryService).summarize(2L, USER_ID, TODAY);
        }

        @Test
        @DisplayName("When portfolios are added up, then keep the oldest and newest dates and every valuation source")
        void whenAllPortfolios_thenMergeDatesAndSources() {
            // Arrange
            givenTwoPortfolios();

            // Act
            InvestorValueEquityDebtCardDTO card = investorValueEquityDebtService.getCard(null);

            // Assert
            assertEquals(LocalDate.of(2026, 6, 30), card.getOldestBalanceDate());
            assertEquals(LocalDate.of(2026, 10, 1), card.getNewestBalanceDate());
            assertEquals(LocalDate.of(2026, 8, 15), card.getOldestValuationDate());
            assertEquals(LocalDate.of(2026, 10, 20), card.getNewestValuationDate());
            assertEquals(LocalDateTime.of(2026, 11, 4, 9, 0), card.getHoldingsUpdatedAt());
            assertEquals(List.of("APPRAISAL", "AVM"), card.getValuationSources());
            assertEquals(InvestorValueEquityDebtServiceImpl.VALUATION_SUPPORTED, card.getValuationSource());
            assertEquals("OWNER_REPORTED_FINANCING", card.getDebtSource());
        }

        @Test
        @DisplayName("When every holding is covered, then the totals are complete")
        void whenEveryHoldingCovered_thenTotalsComplete() {
            // Arrange
            when(portfolioRepository.findByUser_Id(eq(USER_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(portfolio(1L))));
            when(portfolioSummaryRepository.holdings(1L, USER_ID)).thenReturn(List.of(
                    holding(1, "100", HoldingFinancingType.FINANCED, "200000", LocalDate.of(2026, 10, 1))));
            when(portfolioValuationService.latestValues(Set.of(1L), TODAY))
                    .thenReturn(Map.of(1L, valuation("300000", LocalDate.of(2026, 9, 1), "AVM")));

            // Act
            InvestorValueEquityDebtCardDTO card = investorValueEquityDebtService.getCard(null);

            // Assert
            assertMetric(card.getValue(), "COMPLETE", "300000.00", "300000.00", 1, 1);
            assertMetric(card.getDebt(), "COMPLETE", "200000.00", "200000.00", 1, 1);
            assertMetric(card.getEquity(), "COMPLETE", "100000.00", "100000.00", 1, 1);
            assertEquals(new BigDecimal("100.00"), card.getEquityCoveragePercent());
        }

        @Test
        @DisplayName("When no value is known, then value and equity are UNAVAILABLE, never zero")
        void whenNoValue_thenUnavailable() {
            // Arrange
            when(portfolioRepository.findByUser_Id(eq(USER_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(portfolio(1L))));
            when(portfolioSummaryRepository.holdings(1L, USER_ID)).thenReturn(List.of(
                    holding(1, "100", HoldingFinancingType.CASH, null, null)));
            when(portfolioValuationService.latestValues(Set.of(1L), TODAY)).thenReturn(Map.of());

            // Act
            InvestorValueEquityDebtCardDTO card = investorValueEquityDebtService.getCard(null);

            // Assert
            assertMetric(card.getValue(), "UNAVAILABLE", null, "0.00", 0, 1);
            assertMetric(card.getDebt(), "COMPLETE", "0.00", "0.00", 1, 1);
            assertMetric(card.getEquity(), "UNAVAILABLE", null, "0.00", 0, 1);
            assertEquals(new BigDecimal("0.00"), card.getValueCoveragePercent());
            assertEquals(InvestorValueEquityDebtServiceImpl.VALUATION_UNAVAILABLE, card.getValuationSource());
            assertTrue(card.getValuationSources().isEmpty());
        }

        @Test
        @DisplayName("When one owned portfolio is given, then only that portfolio is read")
        void whenOnePortfolio_thenReadOnlyThatPortfolio() {
            // Arrange
            givenTwoPortfolios();
            when(portfolioRepository.findByIdAndUser_Id(2L, USER_ID)).thenReturn(Optional.of(portfolio(2L)));

            // Act
            InvestorValueEquityDebtCardDTO card = investorValueEquityDebtService.getCard(2L);

            // Assert
            assertEquals(InvestorValueEquityDebtCardDTO.SCOPE_PORTFOLIO, card.getScope());
            assertEquals(2L, card.getPortfolioId());
            assertEquals(1, card.getPortfolioCount());
            assertMetric(card.getValue(), "COMPLETE", "250000.00", "250000.00", 1, 1);
            assertMetric(card.getDebt(), "UNAVAILABLE", null, "0.00", 0, 1);
            verify(portfolioValueSummaryService, never()).summarize(eq(1L), anyLong(), any());
            verify(portfolioRepository, never()).findByUser_Id(anyLong(), any());
        }

        @Test
        @DisplayName("When the user has no holdings, then return EMPTY with zero totals and null percentages")
        void whenNoHoldings_thenReturnEmpty() {
            // Arrange
            when(portfolioRepository.findByUser_Id(eq(USER_ID), any())).thenReturn(new PageImpl<>(List.of(portfolio(3L))));
            when(portfolioSummaryRepository.holdings(3L, USER_ID)).thenReturn(List.of());

            // Act
            InvestorValueEquityDebtCardDTO card = investorValueEquityDebtService.getCard(null);

            // Assert
            assertEquals(InvestorValueEquityDebtCardDTO.STATUS_EMPTY, card.getStatus());
            assertEquals(0, card.getTotalHoldings());
            assertMetric(card.getValue(), "EMPTY", "0.00", "0.00", 0, 0);
            assertMetric(card.getDebt(), "EMPTY", "0.00", "0.00", 0, 0);
            assertMetric(card.getEquity(), "EMPTY", "0.00", "0.00", 0, 0);
            assertNull(card.getValueCoveragePercent());
            assertNull(card.getDebtCoveragePercent());
            assertNull(card.getEquityCoveragePercent());
            assertNull(card.getOldestBalanceDate());
            assertNull(card.getHoldingsUpdatedAt());
        }

        @Test
        @DisplayName("When the user has no portfolio, then return EMPTY without calling the value service")
        void whenNoPortfolio_thenReturnEmpty() {
            // Arrange
            when(portfolioRepository.findByUser_Id(eq(USER_ID), any())).thenReturn(new PageImpl<>(List.of()));

            // Act
            InvestorValueEquityDebtCardDTO card = investorValueEquityDebtService.getCard(null);

            // Assert
            assertEquals(InvestorValueEquityDebtCardDTO.STATUS_EMPTY, card.getStatus());
            assertEquals(0, card.getPortfolioCount());
            assertEquals("EMPTY", card.getValue().status());
            verifyNoInteractions(portfolioValueSummaryService);
        }

        @Test
        @DisplayName("When listing portfolios, then read only the current user's, bounded and in ID order")
        void whenListing_thenBoundedOwnPortfolios() {
            // Arrange
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            when(portfolioRepository.findByUser_Id(eq(USER_ID), pageable.capture())).thenReturn(new PageImpl<>(List.of()));

            // Act
            investorValueEquityDebtService.getCard(null);

            // Assert
            assertEquals(InvestorValueEquityDebtServiceImpl.MAX_PORTFOLIOS, pageable.getValue().getPageSize());
            assertEquals(0, pageable.getValue().getPageNumber());
        }

        @Test
        @DisplayName("When the portfolio is missing or belongs to another user, then return 404 and read no holding")
        void whenForeignPortfolio_thenThrowNotFound() {
            // Arrange
            when(portfolioRepository.findByIdAndUser_Id(9L, USER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> investorValueEquityDebtService.getCard(9L));
            assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
            verifyNoInteractions(portfolioValueSummaryService);
        }

        @Test
        @DisplayName("When portfolioId is 0 or negative, then throw IllegalArgumentException")
        void whenPortfolioIdInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> investorValueEquityDebtService.getCard(0L));
            assertEquals("portfolioId must be a positive number", exception.getMessage());
            assertThrows(IllegalArgumentException.class, () -> investorValueEquityDebtService.getCard(-1L));
            verifyNoInteractions(portfolioRepository, portfolioValueSummaryService);
        }
    }
}
