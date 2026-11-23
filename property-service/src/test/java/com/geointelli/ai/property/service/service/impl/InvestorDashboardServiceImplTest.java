package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestorDashboardDTO;
import com.geointelli.ai.property.service.dto.InvestorIncomeExpensesReturnsCardDTO;
import com.geointelli.ai.property.service.dto.InvestorOccupancyExposureCardDTO;
import com.geointelli.ai.property.service.dto.InvestorOpportunitiesCardDTO;
import com.geointelli.ai.property.service.dto.InvestorValueEquityDebtCardDTO;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.InvestorIncomeExpensesReturnsService;
import com.geointelli.ai.property.service.service.InvestorOccupancyExposureService;
import com.geointelli.ai.property.service.service.InvestorOpportunitiesService;
import com.geointelli.ai.property.service.service.InvestorValueEquityDebtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class InvestorDashboardServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final YearMonth JAN = YearMonth.of(2026, 1);
    private static final YearMonth MAR = YearMonth.of(2026, 3);
    private static final InvestorIncomeExpensesReturnsCardDTO.Period PERIOD =
            new InvestorIncomeExpensesReturnsCardDTO.Period(JAN, MAR, LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 3, 31), 3, "UTC");

    CurrentUserService currentUserService = mock(CurrentUserService.class);
    PortfolioRepository portfolioRepository = mock(PortfolioRepository.class);
    InvestorValueEquityDebtService valueEquityDebtService = mock(InvestorValueEquityDebtService.class);
    InvestorIncomeExpensesReturnsService incomeExpensesReturnsService = mock(InvestorIncomeExpensesReturnsService.class);
    InvestorOccupancyExposureService occupancyExposureService = mock(InvestorOccupancyExposureService.class);
    InvestorOpportunitiesService opportunitiesService = mock(InvestorOpportunitiesService.class);

    InvestorDashboardServiceImpl investorDashboardService = new InvestorDashboardServiceImpl(
            currentUserService, portfolioRepository, valueEquityDebtService, incomeExpensesReturnsService,
            occupancyExposureService, opportunitiesService, Clock.fixed(NOW, ZoneOffset.UTC));

    InvestorValueEquityDebtCardDTO valueCard = new InvestorValueEquityDebtCardDTO();
    InvestorIncomeExpensesReturnsCardDTO incomeCard = new InvestorIncomeExpensesReturnsCardDTO();
    InvestorOccupancyExposureCardDTO occupancyCard = new InvestorOccupancyExposureCardDTO();
    InvestorOpportunitiesCardDTO opportunitiesCard = new InvestorOpportunitiesCardDTO();

    private static Portfolio portfolio(long id) {
        Portfolio portfolio = new Portfolio();
        portfolio.setId(id);
        return portfolio;
    }

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(USER_ID);
        when(currentUserService.getCurrentUserEntity()).thenReturn(user);
        when(portfolioRepository.findByIdAndUser_Id(5L, USER_ID)).thenReturn(Optional.of(portfolio(5L)));
        when(incomeExpensesReturnsService.resolvePeriod(JAN, MAR)).thenReturn(PERIOD);
        valueCard.setStatus(InvestorValueEquityDebtCardDTO.STATUS_OK);
        incomeCard.setStatus(InvestorIncomeExpensesReturnsCardDTO.STATUS_OK);
        occupancyCard.setStatus(InvestorOccupancyExposureCardDTO.STATUS_OK);
        opportunitiesCard.setStatus(InvestorOpportunitiesCardDTO.STATUS_OK);
    }

    /** Every card service of portfolio 5 returns its card. */
    private void givenCardsOfPortfolio5() {
        when(valueEquityDebtService.getCard(5L)).thenReturn(valueCard);
        when(incomeExpensesReturnsService.getCard(5L, JAN, MAR)).thenReturn(incomeCard);
        when(occupancyExposureService.getCard(5L)).thenReturn(occupancyCard);
        when(opportunitiesService.getCard(5L, null, null, null)).thenReturn(opportunitiesCard);
    }

    private void verifyNoCardLoaded() {
        verifyNoInteractions(valueEquityDebtService, occupancyExposureService, opportunitiesService);
        verify(incomeExpensesReturnsService, never()).getCard(any(), any(), any());
    }

    @Nested
    @DisplayName("Test Get Dashboard")
    class GetDashboard {

        @Test
        @DisplayName("When every card loads, then return the four cards of the portfolio with status OK")
        void whenEveryCardLoads_thenReturnOk() {
            // Arrange
            givenCardsOfPortfolio5();

            // Act
            InvestorDashboardDTO dashboard = investorDashboardService.getDashboard(5L, JAN, MAR);

            // Assert
            assertEquals(InvestorDashboardDTO.STATUS_OK, dashboard.getStatus());
            assertEquals(InvestorDashboardDTO.SCOPE_PORTFOLIO, dashboard.getScope());
            assertEquals(5L, dashboard.getPortfolioId());
            assertEquals(1, dashboard.getPortfolioCount());
            assertEquals(NOW, dashboard.getGeneratedAt());
            assertEquals(PERIOD, dashboard.getPeriod());
            assertEquals(List.of(), dashboard.getFailedCards());
            assertSame(valueCard, dashboard.getValueEquityDebt());
            assertSame(incomeCard, dashboard.getIncomeExpensesReturns());
            assertSame(occupancyCard, dashboard.getOccupancyExposure());
            assertSame(opportunitiesCard, dashboard.getOpportunities());
            assertNotNull(dashboard.getNote());
        }

        @Test
        @DisplayName("When no portfolio is given, then every card covers all the user's portfolios")
        void whenNoPortfolio_thenAllPortfolios() {
            // Arrange
            YearMonth lastMonth = YearMonth.of(2026, 10);
            YearMonth firstMonth = YearMonth.of(2025, 11);
            InvestorIncomeExpensesReturnsCardDTO.Period lastTwelveMonths = new InvestorIncomeExpensesReturnsCardDTO.Period(
                    firstMonth, lastMonth, firstMonth.atDay(1), lastMonth.atEndOfMonth(), 12, "UTC");
            when(incomeExpensesReturnsService.resolvePeriod(null, null)).thenReturn(lastTwelveMonths);
            when(portfolioRepository.findByUser_Id(eq(USER_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(portfolio(1L), portfolio(2L))));
            when(valueEquityDebtService.getCard(null)).thenReturn(valueCard);
            when(incomeExpensesReturnsService.getCard(null, firstMonth, lastMonth)).thenReturn(incomeCard);
            when(occupancyExposureService.getCard(null)).thenReturn(occupancyCard);
            when(opportunitiesService.getCard(null, null, null, null)).thenReturn(opportunitiesCard);

            // Act
            InvestorDashboardDTO dashboard = investorDashboardService.getDashboard(null, null, null);

            // Assert
            assertEquals(InvestorDashboardDTO.SCOPE_ALL_PORTFOLIOS, dashboard.getScope());
            assertNull(dashboard.getPortfolioId());
            assertEquals(2, dashboard.getPortfolioCount());
            assertEquals(lastTwelveMonths, dashboard.getPeriod());
            // The resolved months are passed to the income card, so both use the same period
            verify(incomeExpensesReturnsService).getCard(null, firstMonth, lastMonth);
        }

        @Test
        @DisplayName("When one card fails, then keep the other cards and return PARTIAL")
        void whenOneCardFails_thenPartial() {
            // Arrange
            givenCardsOfPortfolio5();
            when(opportunitiesService.getCard(5L, null, null, null))
                    .thenThrow(new IllegalStateException("SELECT * FROM opportunity_score failed"));

            // Act
            InvestorDashboardDTO dashboard = investorDashboardService.getDashboard(5L, JAN, MAR);

            // Assert
            assertEquals(InvestorDashboardDTO.STATUS_PARTIAL, dashboard.getStatus());
            assertEquals(List.of(InvestorDashboardDTO.CARD_OPPORTUNITIES), dashboard.getFailedCards());
            assertSame(valueCard, dashboard.getValueEquityDebt());
            assertSame(incomeCard, dashboard.getIncomeExpensesReturns());
            assertSame(occupancyCard, dashboard.getOccupancyExposure());
            InvestorOpportunitiesCardDTO failed = dashboard.getOpportunities();
            assertEquals(InvestorOpportunitiesCardDTO.STATUS_ERROR, failed.getStatus());
            assertEquals(InvestorDashboardServiceImpl.CARD_ERROR, failed.getError());
            assertFalse(failed.getError().contains("SELECT"), "the technical cause is not sent to the client");
            assertEquals(InvestorOpportunitiesCardDTO.SCOPE_PORTFOLIO, failed.getScope());
            assertEquals(5L, failed.getPortfolioId());
            assertEquals(1, failed.getPortfolioCount());
            assertEquals(NOW, failed.getGeneratedAt());
        }

        @Test
        @DisplayName("When the income card fails, then its ERROR card keeps the dashboard period")
        void whenIncomeCardFails_thenErrorCardKeepsPeriod() {
            // Arrange
            givenCardsOfPortfolio5();
            when(incomeExpensesReturnsService.getCard(5L, JAN, MAR)).thenThrow(new RuntimeException("timeout"));

            // Act
            InvestorDashboardDTO dashboard = investorDashboardService.getDashboard(5L, JAN, MAR);

            // Assert
            assertEquals(InvestorDashboardDTO.STATUS_PARTIAL, dashboard.getStatus());
            assertEquals(List.of(InvestorDashboardDTO.CARD_INCOME_EXPENSES_RETURNS), dashboard.getFailedCards());
            assertEquals(InvestorIncomeExpensesReturnsCardDTO.STATUS_ERROR, dashboard.getIncomeExpensesReturns().getStatus());
            assertEquals(PERIOD, dashboard.getIncomeExpensesReturns().getPeriod());
            assertNull(dashboard.getIncomeExpensesReturns().getActual());
        }

        @Test
        @DisplayName("When every card fails, then return four ERROR cards with status ERROR")
        void whenEveryCardFails_thenError() {
            // Arrange
            when(valueEquityDebtService.getCard(5L)).thenThrow(new RuntimeException("down"));
            when(incomeExpensesReturnsService.getCard(5L, JAN, MAR)).thenThrow(new RuntimeException("down"));
            when(occupancyExposureService.getCard(5L)).thenThrow(new RuntimeException("down"));
            when(opportunitiesService.getCard(5L, null, null, null)).thenThrow(new RuntimeException("down"));

            // Act
            InvestorDashboardDTO dashboard = investorDashboardService.getDashboard(5L, JAN, MAR);

            // Assert
            assertEquals(InvestorDashboardDTO.STATUS_ERROR, dashboard.getStatus());
            assertEquals(List.of(InvestorDashboardDTO.CARD_VALUE_EQUITY_DEBT,
                    InvestorDashboardDTO.CARD_INCOME_EXPENSES_RETURNS, InvestorDashboardDTO.CARD_OCCUPANCY_EXPOSURE,
                    InvestorDashboardDTO.CARD_OPPORTUNITIES), dashboard.getFailedCards());
            assertEquals(InvestorValueEquityDebtCardDTO.STATUS_ERROR, dashboard.getValueEquityDebt().getStatus());
            assertEquals(InvestorOccupancyExposureCardDTO.STATUS_ERROR, dashboard.getOccupancyExposure().getStatus());
        }

        @Test
        @DisplayName("When every card is empty, then return status EMPTY")
        void whenEveryCardEmpty_thenEmpty() {
            // Arrange
            valueCard.setStatus(InvestorValueEquityDebtCardDTO.STATUS_EMPTY);
            incomeCard.setStatus(InvestorIncomeExpensesReturnsCardDTO.STATUS_EMPTY);
            occupancyCard.setStatus(InvestorOccupancyExposureCardDTO.STATUS_EMPTY);
            opportunitiesCard.setStatus(InvestorOpportunitiesCardDTO.STATUS_EMPTY);
            givenCardsOfPortfolio5();

            // Act & Assert
            assertEquals(InvestorDashboardDTO.STATUS_EMPTY,
                    investorDashboardService.getDashboard(5L, JAN, MAR).getStatus());
        }

        @Test
        @DisplayName("When only some cards are empty, then return status OK")
        void whenSomeCardsEmpty_thenOk() {
            // Arrange: no holding, but stored opportunity scores exist
            valueCard.setStatus(InvestorValueEquityDebtCardDTO.STATUS_EMPTY);
            incomeCard.setStatus(InvestorIncomeExpensesReturnsCardDTO.STATUS_EMPTY);
            occupancyCard.setStatus(InvestorOccupancyExposureCardDTO.STATUS_EMPTY);
            givenCardsOfPortfolio5();

            // Act & Assert
            assertEquals(InvestorDashboardDTO.STATUS_OK,
                    investorDashboardService.getDashboard(5L, JAN, MAR).getStatus());
        }

        @Test
        @DisplayName("When portfolioId is not positive, then throw 400 before loading any card")
        void whenPortfolioIdNotPositive_thenThrow() {
            // Act & Assert
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> investorDashboardService.getDashboard(0L, JAN, MAR));
            assertEquals("portfolioId must be a positive number", e.getMessage());
            verifyNoCardLoaded();
            verifyNoInteractions(currentUserService, portfolioRepository);
        }

        @Test
        @DisplayName("When the period is invalid, then throw 400 before loading any card")
        void whenPeriodInvalid_thenThrow() {
            // Arrange
            when(incomeExpensesReturnsService.resolvePeriod(JAN, null))
                    .thenThrow(new IllegalArgumentException("fromMonth and toMonth must be given together"));

            // Act & Assert
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> investorDashboardService.getDashboard(5L, JAN, null));
            assertEquals("fromMonth and toMonth must be given together", e.getMessage());
            verifyNoCardLoaded();
        }

        @Test
        @DisplayName("When the portfolio belongs to another user, then throw 404 before loading any card")
        void whenForeignPortfolio_thenThrow404() {
            // Arrange
            when(portfolioRepository.findByIdAndUser_Id(9L, USER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> investorDashboardService.getDashboard(9L, JAN, MAR));
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
            verifyNoCardLoaded();
        }
    }
}
