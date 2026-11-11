package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestorValueEquityDebtCardDTO;
import com.geointelli.ai.property.service.dto.PortfolioMetricDTO;
import com.geointelli.ai.property.service.dto.PortfolioValueSummaryDTO;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.InvestorValueEquityDebtService;
import com.geointelli.ai.property.service.service.PortfolioValueSummaryService;
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
 * Calls the SCRUM-74 value summary once per owned portfolio and adds the results up. Amounts are already split
 * by ownership share and rounded per holding, so the sums stay exact; a total is complete only when every holding
 * of every included portfolio is covered.
 */
@Service
public class InvestorValueEquityDebtServiceImpl implements InvestorValueEquityDebtService {

    static final int MAX_PORTFOLIOS = 100;
    static final String VALUATION_SUPPORTED = "SUPPORTED_VALUATION_SERVICE";
    static final String VALUATION_UNAVAILABLE = "UNAVAILABLE_PENDING_SCRUM_58";
    static final String DEBT_SOURCE = "OWNER_REPORTED_FINANCING";
    static final String NOTE = "Adds up the SCRUM-74 value summaries of the included portfolios. "
            + "Amounts apply the current ownership share; equity = value - debt only for holdings where both are known. "
            + "total is null when a holding is not covered; knownTotal is then the partial sum. "
            + "Missing values or balances are never counted as zero.";

    private final CurrentUserService currentUserService;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioValueSummaryService portfolioValueSummaryService;
    private final Clock clock;

    public InvestorValueEquityDebtServiceImpl(CurrentUserService currentUserService,
                                              PortfolioRepository portfolioRepository,
                                              PortfolioValueSummaryService portfolioValueSummaryService,
                                              @Qualifier("portfolioSummaryClock") Clock clock) {
        this.currentUserService = currentUserService;
        this.portfolioRepository = portfolioRepository;
        this.portfolioValueSummaryService = portfolioValueSummaryService;
        this.clock = clock;
    }

    @Override
    public InvestorValueEquityDebtCardDTO getCard(Long portfolioId) {
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
        MetricTotal value = new MetricTotal();
        MetricTotal debt = new MetricTotal();
        MetricTotal equity = new MetricTotal();
        long totalHoldings = 0;
        LocalDate oldestBalance = null;
        LocalDate newestBalance = null;
        LocalDate oldestValuation = null;
        LocalDate newestValuation = null;
        LocalDateTime holdingsUpdatedAt = null;
        Set<String> sources = new TreeSet<>();
        for (Long id : portfolioIds) {
            PortfolioValueSummaryDTO summary = portfolioValueSummaryService.summarize(id, userId, today);
            totalHoldings += summary.value().totalHoldings();
            value.add(summary.value());
            debt.add(summary.debt());
            equity.add(summary.equity());
            oldestBalance = earliest(oldestBalance, summary.oldestBalanceDate());
            newestBalance = latest(newestBalance, summary.newestBalanceDate());
            oldestValuation = earliest(oldestValuation, summary.oldestValuationDate());
            newestValuation = latest(newestValuation, summary.newestValuationDate());
            if (summary.holdingsUpdatedAt() != null
                    && (holdingsUpdatedAt == null || summary.holdingsUpdatedAt().isAfter(holdingsUpdatedAt))) {
                holdingsUpdatedAt = summary.holdingsUpdatedAt();
            }
            sources.addAll(summary.valuationSources());
        }

        InvestorValueEquityDebtCardDTO card = new InvestorValueEquityDebtCardDTO();
        card.setScope(portfolioId == null
                ? InvestorValueEquityDebtCardDTO.SCOPE_ALL_PORTFOLIOS : InvestorValueEquityDebtCardDTO.SCOPE_PORTFOLIO);
        card.setPortfolioId(portfolioId);
        card.setPortfolioCount(portfolioIds.size());
        card.setStatus(totalHoldings == 0
                ? InvestorValueEquityDebtCardDTO.STATUS_EMPTY : InvestorValueEquityDebtCardDTO.STATUS_OK);
        card.setGeneratedAt(generatedAt);
        card.setAsOfDate(today);
        card.setCurrency("USD");
        card.setTotalHoldings(totalHoldings);
        card.setValue(value.toMetric(totalHoldings));
        card.setDebt(debt.toMetric(totalHoldings));
        card.setEquity(equity.toMetric(totalHoldings));
        card.setValueCoveragePercent(percent(value.covered, totalHoldings));
        card.setDebtCoveragePercent(percent(debt.covered, totalHoldings));
        card.setEquityCoveragePercent(percent(equity.covered, totalHoldings));
        card.setOldestBalanceDate(oldestBalance);
        card.setNewestBalanceDate(newestBalance);
        card.setOldestValuationDate(oldestValuation);
        card.setNewestValuationDate(newestValuation);
        card.setHoldingsUpdatedAt(holdingsUpdatedAt);
        card.setValuationSources(List.copyOf(sources));
        card.setValuationSource(value.covered > 0 ? VALUATION_SUPPORTED : VALUATION_UNAVAILABLE);
        card.setDebtSource(DEBT_SOURCE);
        card.setNote(NOTE);
        return card;
    }

    /** The earlier of two dates, ignoring null. */
    private static LocalDate earliest(LocalDate current, LocalDate candidate) {
        return candidate != null && (current == null || candidate.isBefore(current)) ? candidate : current;
    }

    /** The later of two dates, ignoring null. */
    private static LocalDate latest(LocalDate current, LocalDate candidate) {
        return candidate != null && (current == null || candidate.isAfter(current)) ? candidate : current;
    }

    /** count / total × 100 with 2 decimals; null when the denominator is 0. */
    private static BigDecimal percent(long count, long total) {
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /** Running total of one amount (value, debt or equity) across portfolios. */
    private static final class MetricTotal {
        private BigDecimal knownTotal = BigDecimal.ZERO;
        private long covered;

        private void add(PortfolioMetricDTO metric) {
            knownTotal = knownTotal.add(metric.knownTotal());
            covered += metric.coveredHoldings();
        }

        /** Same status rule as SCRUM-74: EMPTY, COMPLETE, UNAVAILABLE or PARTIAL. */
        private PortfolioMetricDTO toMetric(long totalHoldings) {
            BigDecimal rounded = knownTotal.setScale(2, RoundingMode.HALF_UP);
            String status = totalHoldings == 0 ? "EMPTY"
                    : covered == totalHoldings ? "COMPLETE"
                    : covered == 0 ? "UNAVAILABLE" : "PARTIAL";
            return new PortfolioMetricDTO(status, covered == totalHoldings ? rounded : null, rounded,
                    covered, totalHoldings);
        }
    }
}
