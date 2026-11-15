package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestorOccupancyExposureCardDTO;
import com.geointelli.ai.property.service.dto.PortfolioExposureSummaryDTO;
import com.geointelli.ai.property.service.dto.PortfolioGeographyDTO;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.InvestorOccupancyExposureService;
import com.geointelli.ai.property.service.service.PortfolioExposureSummaryService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * Calls the SCRUM-74 exposure summary once per owned portfolio and adds the results up; percentages are
 * recalculated over all included holdings, and unknown occupancy and geography stay explicit categories.
 */
@Service
public class InvestorOccupancyExposureServiceImpl implements InvestorOccupancyExposureService {

    static final int MAX_PORTFOLIOS = 100;
    static final String NOTE = "Adds up the SCRUM-74 exposure summaries of the included portfolios. "
            + "Current owner-reported occupancy; rental occupancy = RENTED / (RENTED + VACANT). "
            + "Exposure counts holdings, not values; unknown occupancy and geography are listed, not dropped.";

    private final CurrentUserService currentUserService;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioExposureSummaryService portfolioExposureSummaryService;
    private final Clock clock;

    public InvestorOccupancyExposureServiceImpl(CurrentUserService currentUserService,
                                                PortfolioRepository portfolioRepository,
                                                PortfolioExposureSummaryService portfolioExposureSummaryService,
                                                @Qualifier("portfolioSummaryClock") Clock clock) {
        this.currentUserService = currentUserService;
        this.portfolioRepository = portfolioRepository;
        this.portfolioExposureSummaryService = portfolioExposureSummaryService;
        this.clock = clock;
    }

    @Override
    public InvestorOccupancyExposureCardDTO getCard(Long portfolioId) {
        if (portfolioId != null && portfolioId <= 0) {
            throw new IllegalArgumentException("portfolioId must be a positive number");
        }
        Long userId = currentUserService.getCurrentUserEntity().getId();
        Instant generatedAt = clock.instant();
        LocalDate today = generatedAt.atZone(ZoneOffset.UTC).toLocalDate();

        // Only the current user's portfolios: a foreign portfolio is the same 404 as a missing one
        List<Long> portfolioIds;
        if (portfolioId != null) {
            portfolioRepository.findByIdAndUser_Id(portfolioId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
            portfolioIds = List.of(portfolioId);
        } else {
            portfolioIds = portfolioRepository
                    .findByUser_Id(userId, PageRequest.of(0, MAX_PORTFOLIOS, Sort.by(Sort.Direction.ASC, "id")))
                    .map(Portfolio::getId)
                    .getContent();
        }
        // Add up the existing per-portfolio summaries
        Map<HoldingOccupancyStatus, Long> occupancyCounts = new EnumMap<>(HoldingOccupancyStatus.class);
        for (HoldingOccupancyStatus status : HoldingOccupancyStatus.values()) {
            occupancyCounts.put(status, 0L);
        }
        Map<String, GeographyTotal> states = new TreeMap<>();
        Map<String, GeographyTotal> counties = new TreeMap<>();
        long totalHoldings = 0;
        LocalDateTime holdingsUpdatedAt = null;
        for (Long id : portfolioIds) {
            PortfolioExposureSummaryDTO summary = portfolioExposureSummaryService.summarize(id, userId, today);
            totalHoldings += summary.totalHoldings();
            summary.occupancyCounts().forEach((status, count) -> occupancyCounts.merge(status, count, Long::sum));
            for (PortfolioGeographyDTO state : summary.states()) {
                String key = state.stateId() == null ? "UNKNOWN" : "STATE:" + state.stateId();
                states.computeIfAbsent(key, k -> new GeographyTotal(state)).add(state);
            }
            for (PortfolioGeographyDTO county : summary.counties()) {
                String key = county.countyId() == null ? "UNKNOWN" : "COUNTY:" + county.countyId();
                counties.computeIfAbsent(key, k -> new GeographyTotal(county)).add(county);
            }
            if (summary.holdingsUpdatedAt() != null
                    && (holdingsUpdatedAt == null || summary.holdingsUpdatedAt().isAfter(holdingsUpdatedAt))) {
                holdingsUpdatedAt = summary.holdingsUpdatedAt();
            }
        }

        long knownOccupancy = totalHoldings - occupancyCounts.get(HoldingOccupancyStatus.UNKNOWN);
        long rented = occupancyCounts.get(HoldingOccupancyStatus.RENTED);
        long rental = rented + occupancyCounts.get(HoldingOccupancyStatus.VACANT);
        GeographyTotal unknownState = states.get("UNKNOWN");
        long knownGeography = totalHoldings - (unknownState == null ? 0 : unknownState.holdingCount);

        InvestorOccupancyExposureCardDTO card = new InvestorOccupancyExposureCardDTO();
        card.setScope(portfolioId == null
                ? InvestorOccupancyExposureCardDTO.SCOPE_ALL_PORTFOLIOS : InvestorOccupancyExposureCardDTO.SCOPE_PORTFOLIO);
        card.setPortfolioId(portfolioId);
        card.setPortfolioCount(portfolioIds.size());
        card.setStatus(totalHoldings == 0
                ? InvestorOccupancyExposureCardDTO.STATUS_EMPTY : InvestorOccupancyExposureCardDTO.STATUS_OK);
        card.setGeneratedAt(generatedAt);
        card.setAsOfDate(today);
        card.setTotalHoldings(totalHoldings);
        card.setOccupancyCounts(occupancyCounts);
        card.setKnownOccupancyHoldings(knownOccupancy);
        card.setOccupancyCoveragePercent(percent(knownOccupancy, totalHoldings));
        card.setRentalHoldings(rental);
        card.setRentalOccupancyPercent(percent(rented, rental));
        card.setKnownGeographyHoldings(knownGeography);
        card.setGeographyCoveragePercent(percent(knownGeography, totalHoldings));
        card.setExposureBasis("HOLDING_COUNT");
        card.setStates(toGeography(states, totalHoldings));
        card.setCounties(toGeography(counties, totalHoldings));
        card.setHoldingsUpdatedAt(holdingsUpdatedAt);
        card.setNote(NOTE);
        return card;
    }

    private static List<PortfolioGeographyDTO> toGeography(Map<String, GeographyTotal> groups, long totalHoldings) {
        List<PortfolioGeographyDTO> result = new ArrayList<>();
        for (GeographyTotal group : groups.values()) {
            PortfolioGeographyDTO first = group.first;
            result.add(new PortfolioGeographyDTO(first.stateId(), first.stateCode(), first.stateName(),
                    first.countyId(), first.countyName(), first.unknown(), group.holdingCount,
                    percent(group.holdingCount, totalHoldings), group.ownershipEquivalent));
        }
        return List.copyOf(result);
    }

    /** count / total × 100 with 2 decimals; null when the denominator is 0. */
    private static BigDecimal percent(long count, long total) {
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /** Running total of one state or county across portfolios. */
    private static final class GeographyTotal {
        private final PortfolioGeographyDTO first;
        private long holdingCount;
        private BigDecimal ownershipEquivalent = BigDecimal.ZERO;

        private GeographyTotal(PortfolioGeographyDTO first) {
            this.first = first;
        }

        private void add(PortfolioGeographyDTO geography) {
            holdingCount += geography.holdingCount();
            ownershipEquivalent = ownershipEquivalent.add(geography.ownershipEquivalentHoldings());
        }
    }
}
