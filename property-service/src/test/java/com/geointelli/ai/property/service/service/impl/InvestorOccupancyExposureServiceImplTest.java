package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestorOccupancyExposureCardDTO;
import com.geointelli.ai.property.service.dto.PortfolioGeographyDTO;
import com.geointelli.ai.property.service.entity.HoldingFinancingType;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.PortfolioHoldingSnapshot;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.repository.PortfolioSummaryRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PortfolioExposureSummaryService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class InvestorOccupancyExposureServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    CurrentUserService currentUserService = mock(CurrentUserService.class);
    PortfolioRepository portfolioRepository = mock(PortfolioRepository.class);
    PortfolioSummaryRepository portfolioSummaryRepository = mock(PortfolioSummaryRepository.class);
    // The real SCRUM-74 exposure service, spied to check that it is reused once per portfolio
    PortfolioExposureSummaryService portfolioExposureSummaryService =
            spy(new PortfolioExposureSummaryServiceImpl(portfolioSummaryRepository));

    InvestorOccupancyExposureServiceImpl investorOccupancyExposureService = new InvestorOccupancyExposureServiceImpl(
            currentUserService, portfolioRepository, portfolioExposureSummaryService,
            Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC));

    private static PortfolioHoldingSnapshot holding(long id, HoldingOccupancyStatus occupancy, Long countyId,
                                                    Long stateId, String stateCode, String sharePercent) {
        return new PortfolioHoldingSnapshot(id, id, new BigDecimal(sharePercent), HoldingFinancingType.UNKNOWN,
                null, null, occupancy, countyId, countyId == null ? null : "County " + countyId,
                stateId, stateCode, stateCode, LocalDateTime.of(2026, 11, (int) id, 9, 0));
    }

    private static Portfolio portfolio(long id) {
        Portfolio portfolio = new Portfolio();
        portfolio.setId(id);
        return portfolio;
    }

    /** Ali: "Florida" (3 holdings) and "Texas" (2 holdings, one with unknown occupancy and geography). */
    private void givenTwoPortfolios() {
        when(portfolioRepository.findByUser_Id(eq(USER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(portfolio(1L), portfolio(2L))));
        when(portfolioSummaryRepository.holdings(1L, USER_ID)).thenReturn(List.of(
                holding(1, HoldingOccupancyStatus.RENTED, 10L, 1L, "FL", "100"),
                holding(2, HoldingOccupancyStatus.VACANT, 10L, 1L, "FL", "50"),
                holding(3, HoldingOccupancyStatus.RENTED, 11L, 1L, "FL", "100")));
        when(portfolioSummaryRepository.holdings(2L, USER_ID)).thenReturn(List.of(
                holding(4, HoldingOccupancyStatus.OWNER_OCCUPIED, 20L, 2L, "TX", "100"),
                holding(5, null, null, null, null, "100")));
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
        @DisplayName("When no portfolio is given, then add up every portfolio's occupancy with coverage")
        void whenAllPortfolios_thenAddUpOccupancyWithCoverage() {
            // Arrange
            givenTwoPortfolios();

            // Act
            InvestorOccupancyExposureCardDTO card = investorOccupancyExposureService.getCard(null);

            // Assert
            assertEquals(InvestorOccupancyExposureCardDTO.SCOPE_ALL_PORTFOLIOS, card.getScope());
            assertNull(card.getPortfolioId());
            assertEquals(2, card.getPortfolioCount());
            assertEquals(InvestorOccupancyExposureCardDTO.STATUS_OK, card.getStatus());
            assertNull(card.getError());
            assertEquals(TODAY, card.getAsOfDate());
            assertEquals(5, card.getTotalHoldings());
            assertEquals(Map.of(HoldingOccupancyStatus.UNKNOWN, 1L, HoldingOccupancyStatus.OWNER_OCCUPIED, 1L,
                    HoldingOccupancyStatus.RENTED, 2L, HoldingOccupancyStatus.VACANT, 1L), card.getOccupancyCounts());
            assertEquals(4, card.getKnownOccupancyHoldings());
            assertEquals(new BigDecimal("80.00"), card.getOccupancyCoveragePercent());
            assertEquals(3, card.getRentalHoldings());
            assertEquals(new BigDecimal("66.67"), card.getRentalOccupancyPercent());
            assertEquals(LocalDateTime.of(2026, 11, 5, 9, 0), card.getHoldingsUpdatedAt());
            verify(portfolioExposureSummaryService).summarize(1L, USER_ID, TODAY);
            verify(portfolioExposureSummaryService).summarize(2L, USER_ID, TODAY);
        }

        @Test
        @DisplayName("When geography is added up, then percentages use all holdings and unknown geography is kept")
        void whenAllPortfolios_thenMergeGeographyAndKeepUnknown() {
            // Arrange
            givenTwoPortfolios();

            // Act
            InvestorOccupancyExposureCardDTO card = investorOccupancyExposureService.getCard(null);

            // Assert
            assertEquals("HOLDING_COUNT", card.getExposureBasis());
            List<PortfolioGeographyDTO> states = card.getStates();
            assertEquals(3, states.size());
            assertEquals("FL", states.get(0).stateCode());
            assertEquals(3, states.get(0).holdingCount());
            assertEquals(new BigDecimal("60.00"), states.get(0).holdingPercent());
            assertEquals(new BigDecimal("2.5000"), states.get(0).ownershipEquivalentHoldings());
            assertEquals("TX", states.get(1).stateCode());
            assertEquals(new BigDecimal("20.00"), states.get(1).holdingPercent());
            assertTrue(states.get(2).unknown());
            assertEquals(1, states.get(2).holdingCount());
            assertEquals(4, card.getKnownGeographyHoldings());
            assertEquals(new BigDecimal("80.00"), card.getGeographyCoveragePercent());

            List<PortfolioGeographyDTO> counties = card.getCounties();
            assertEquals(List.of(10L, 11L, 20L), counties.subList(0, 3).stream().map(PortfolioGeographyDTO::countyId).toList());
            assertEquals(2, counties.get(0).holdingCount());
            assertEquals(new BigDecimal("40.00"), counties.get(0).holdingPercent());
            assertTrue(counties.get(3).unknown());
        }

        @Test
        @DisplayName("When the same state is in two portfolios, then it is one entry with the summed holdings")
        void whenSameStateInTwoPortfolios_thenOneEntry() {
            // Arrange
            when(portfolioRepository.findByUser_Id(eq(USER_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(portfolio(1L), portfolio(2L))));
            when(portfolioSummaryRepository.holdings(1L, USER_ID))
                    .thenReturn(List.of(holding(1, HoldingOccupancyStatus.RENTED, 10L, 1L, "FL", "100")));
            when(portfolioSummaryRepository.holdings(2L, USER_ID))
                    .thenReturn(List.of(holding(2, HoldingOccupancyStatus.RENTED, 10L, 1L, "FL", "100")));

            // Act
            InvestorOccupancyExposureCardDTO card = investorOccupancyExposureService.getCard(null);

            // Assert
            assertEquals(1, card.getStates().size());
            assertEquals(2, card.getStates().get(0).holdingCount());
            assertEquals(new BigDecimal("100.00"), card.getStates().get(0).holdingPercent());
            assertEquals(1, card.getCounties().size());
        }

        @Test
        @DisplayName("When one owned portfolio is given, then only that portfolio is read")
        void whenOnePortfolio_thenReadOnlyThatPortfolio() {
            // Arrange
            givenTwoPortfolios();
            when(portfolioRepository.findByIdAndUser_Id(2L, USER_ID)).thenReturn(Optional.of(portfolio(2L)));

            // Act
            InvestorOccupancyExposureCardDTO card = investorOccupancyExposureService.getCard(2L);

            // Assert
            assertEquals(InvestorOccupancyExposureCardDTO.SCOPE_PORTFOLIO, card.getScope());
            assertEquals(2L, card.getPortfolioId());
            assertEquals(1, card.getPortfolioCount());
            assertEquals(2, card.getTotalHoldings());
            assertNull(card.getRentalOccupancyPercent());
            verify(portfolioExposureSummaryService, never()).summarize(eq(1L), anyLong(), any());
            verify(portfolioRepository, never()).findByUser_Id(anyLong(), any());
        }

        @Test
        @DisplayName("When the user has no holdings, then return EMPTY with zero counts, empty lists and null percentages")
        void whenNoHoldings_thenReturnEmpty() {
            // Arrange
            when(portfolioRepository.findByUser_Id(eq(USER_ID), any())).thenReturn(new PageImpl<>(List.of(portfolio(3L))));
            when(portfolioSummaryRepository.holdings(3L, USER_ID)).thenReturn(List.of());

            // Act
            InvestorOccupancyExposureCardDTO card = investorOccupancyExposureService.getCard(null);

            // Assert
            assertEquals(InvestorOccupancyExposureCardDTO.STATUS_EMPTY, card.getStatus());
            assertEquals(0, card.getTotalHoldings());
            assertEquals(4, card.getOccupancyCounts().size());
            assertTrue(card.getOccupancyCounts().values().stream().allMatch(count -> count == 0));
            assertTrue(card.getStates().isEmpty());
            assertTrue(card.getCounties().isEmpty());
            assertNull(card.getOccupancyCoveragePercent());
            assertNull(card.getGeographyCoveragePercent());
            assertNull(card.getRentalOccupancyPercent());
            assertNull(card.getHoldingsUpdatedAt());
        }

        @Test
        @DisplayName("When the user has no portfolio, then return EMPTY without calling the exposure service")
        void whenNoPortfolio_thenReturnEmpty() {
            // Arrange
            when(portfolioRepository.findByUser_Id(eq(USER_ID), any())).thenReturn(new PageImpl<>(List.of()));

            // Act
            InvestorOccupancyExposureCardDTO card = investorOccupancyExposureService.getCard(null);

            // Assert
            assertEquals(InvestorOccupancyExposureCardDTO.STATUS_EMPTY, card.getStatus());
            assertEquals(0, card.getPortfolioCount());
            verifyNoInteractions(portfolioExposureSummaryService);
        }

        @Test
        @DisplayName("When listing portfolios, then read only the current user's, bounded and in ID order")
        void whenListing_thenBoundedOwnPortfolios() {
            // Arrange
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            when(portfolioRepository.findByUser_Id(eq(USER_ID), pageable.capture())).thenReturn(new PageImpl<>(List.of()));

            // Act
            investorOccupancyExposureService.getCard(null);

            // Assert
            assertEquals(InvestorOccupancyExposureServiceImpl.MAX_PORTFOLIOS, pageable.getValue().getPageSize());
            assertEquals(0, pageable.getValue().getPageNumber());
        }

        @Test
        @DisplayName("When the portfolio is missing or belongs to another user, then return 404 and read no holding")
        void whenForeignPortfolio_thenThrowNotFound() {
            // Arrange
            when(portfolioRepository.findByIdAndUser_Id(9L, USER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> investorOccupancyExposureService.getCard(9L));
            assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
            verifyNoInteractions(portfolioExposureSummaryService);
        }

        @Test
        @DisplayName("When portfolioId is 0 or negative, then throw IllegalArgumentException")
        void whenPortfolioIdInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> investorOccupancyExposureService.getCard(0L));
            assertEquals("portfolioId must be a positive number", exception.getMessage());
            assertThrows(IllegalArgumentException.class, () -> investorOccupancyExposureService.getCard(-1L));
            verifyNoInteractions(portfolioRepository, portfolioExposureSummaryService);
        }
    }
}
