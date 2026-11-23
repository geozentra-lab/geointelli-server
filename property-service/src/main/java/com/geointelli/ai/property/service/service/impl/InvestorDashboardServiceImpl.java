package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestorDashboardDTO;
import com.geointelli.ai.property.service.dto.InvestorIncomeExpensesReturnsCardDTO;
import com.geointelli.ai.property.service.dto.InvestorOccupancyExposureCardDTO;
import com.geointelli.ai.property.service.dto.InvestorOpportunitiesCardDTO;
import com.geointelli.ai.property.service.dto.InvestorValueEquityDebtCardDTO;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.InvestorDashboardService;
import com.geointelli.ai.property.service.service.InvestorIncomeExpensesReturnsService;
import com.geointelli.ai.property.service.service.InvestorOccupancyExposureService;
import com.geointelli.ai.property.service.service.InvestorOpportunitiesService;
import com.geointelli.ai.property.service.service.InvestorValueEquityDebtService;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Validates the scope and the period once, then calls each investor card service for that scope. Each card is
 * loaded on its own: when one throws, it is replaced by an ERROR card and the others are kept. This class is
 * deliberately not {@code @Transactional}: a card failing inside a shared transaction would mark it rollback-only
 * and make the whole response fail.
 */
@Slf4j
@Service
public class InvestorDashboardServiceImpl implements InvestorDashboardService {

    static final int MAX_PORTFOLIOS = 100;
    static final String CARD_ERROR = "This card could not be loaded; the other cards are not affected.";
    static final String NOTE = "The four investor cards for the same portfolio scope. Only the income, expenses and "
            + "returns card uses period; the other cards show the current state. A card that could not be loaded "
            + "has status ERROR and is listed in failedCards; the other cards are kept. The opportunities card uses "
            + "its default limit and no county or state filter.";

    private final CurrentUserService currentUserService;
    private final PortfolioRepository portfolioRepository;
    private final InvestorValueEquityDebtService investorValueEquityDebtService;
    private final InvestorIncomeExpensesReturnsService investorIncomeExpensesReturnsService;
    private final InvestorOccupancyExposureService investorOccupancyExposureService;
    private final InvestorOpportunitiesService investorOpportunitiesService;
    private final Clock clock;

    public InvestorDashboardServiceImpl(CurrentUserService currentUserService,
                                        PortfolioRepository portfolioRepository,
                                        InvestorValueEquityDebtService investorValueEquityDebtService,
                                        InvestorIncomeExpensesReturnsService investorIncomeExpensesReturnsService,
                                        InvestorOccupancyExposureService investorOccupancyExposureService,
                                        InvestorOpportunitiesService investorOpportunitiesService,
                                        @Qualifier("portfolioSummaryClock") Clock clock) {
        this.currentUserService = currentUserService;
        this.portfolioRepository = portfolioRepository;
        this.investorValueEquityDebtService = investorValueEquityDebtService;
        this.investorIncomeExpensesReturnsService = investorIncomeExpensesReturnsService;
        this.investorOccupancyExposureService = investorOccupancyExposureService;
        this.investorOpportunitiesService = investorOpportunitiesService;
        this.clock = clock;
    }

    /** {@inheritDoc} */
    @Override
    public InvestorDashboardDTO getDashboard(Long portfolioId, YearMonth fromMonth, YearMonth toMonth) {
        // 1. Invalid input fails the whole request (400 or 404), before any card is loaded
        if (portfolioId != null && portfolioId <= 0) {
            throw new IllegalArgumentException("portfolioId must be a positive number");
        }
        InvestorIncomeExpensesReturnsCardDTO.Period period =
                investorIncomeExpensesReturnsService.resolvePeriod(fromMonth, toMonth);
        Long userId = currentUserService.getCurrentUserEntity().getId();
        int portfolioCount;
        if (portfolioId != null) {
            portfolioRepository.findByIdAndUser_Id(portfolioId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
            portfolioCount = 1;
        } else {
            portfolioCount = portfolioRepository
                    .findByUser_Id(userId, PageRequest.of(0, MAX_PORTFOLIOS, Sort.by(Sort.Direction.ASC, "id")))
                    .getNumberOfElements();
        }

        InvestorDashboardDTO dashboard = new InvestorDashboardDTO();
        dashboard.setScope(portfolioId == null
                ? InvestorDashboardDTO.SCOPE_ALL_PORTFOLIOS : InvestorDashboardDTO.SCOPE_PORTFOLIO);
        dashboard.setPortfolioId(portfolioId);
        dashboard.setPortfolioCount(portfolioCount);
        dashboard.setGeneratedAt(clock.instant());
        dashboard.setPeriod(period);
        dashboard.setNote(NOTE);

        // 2. Each card alone: a failure becomes an ERROR card, the other cards are kept
        List<String> failedCards = new ArrayList<>();
        dashboard.setValueEquityDebt(load(InvestorDashboardDTO.CARD_VALUE_EQUITY_DEBT, failedCards,
                () -> investorValueEquityDebtService.getCard(portfolioId),
                () -> {
                    InvestorValueEquityDebtCardDTO card = new InvestorValueEquityDebtCardDTO();
                    card.setScope(dashboard.getScope());
                    card.setPortfolioId(portfolioId);
                    card.setPortfolioCount(portfolioCount);
                    card.setStatus(InvestorValueEquityDebtCardDTO.STATUS_ERROR);
                    card.setError(CARD_ERROR);
                    card.setGeneratedAt(dashboard.getGeneratedAt());
                    return card;
                }));
        // The resolved months are passed, so the card uses exactly the dashboard period
        dashboard.setIncomeExpensesReturns(load(InvestorDashboardDTO.CARD_INCOME_EXPENSES_RETURNS, failedCards,
                () -> investorIncomeExpensesReturnsService.getCard(portfolioId, period.fromMonth(), period.toMonth()),
                () -> {
                    InvestorIncomeExpensesReturnsCardDTO card = new InvestorIncomeExpensesReturnsCardDTO();
                    card.setScope(dashboard.getScope());
                    card.setPortfolioId(portfolioId);
                    card.setPortfolioCount(portfolioCount);
                    card.setStatus(InvestorIncomeExpensesReturnsCardDTO.STATUS_ERROR);
                    card.setError(CARD_ERROR);
                    card.setGeneratedAt(dashboard.getGeneratedAt());
                    card.setPeriod(period);
                    return card;
                }));
        dashboard.setOccupancyExposure(load(InvestorDashboardDTO.CARD_OCCUPANCY_EXPOSURE, failedCards,
                () -> investorOccupancyExposureService.getCard(portfolioId),
                () -> {
                    InvestorOccupancyExposureCardDTO card = new InvestorOccupancyExposureCardDTO();
                    card.setScope(dashboard.getScope());
                    card.setPortfolioId(portfolioId);
                    card.setPortfolioCount(portfolioCount);
                    card.setStatus(InvestorOccupancyExposureCardDTO.STATUS_ERROR);
                    card.setError(CARD_ERROR);
                    card.setGeneratedAt(dashboard.getGeneratedAt());
                    return card;
                }));
        dashboard.setOpportunities(load(InvestorDashboardDTO.CARD_OPPORTUNITIES, failedCards,
                () -> investorOpportunitiesService.getCard(portfolioId, null, null, null),
                () -> {
                    InvestorOpportunitiesCardDTO card = new InvestorOpportunitiesCardDTO();
                    card.setScope(dashboard.getScope());
                    card.setPortfolioId(portfolioId);
                    card.setPortfolioCount(portfolioCount);
                    card.setStatus(InvestorOpportunitiesCardDTO.STATUS_ERROR);
                    card.setError(CARD_ERROR);
                    card.setGeneratedAt(dashboard.getGeneratedAt());
                    return card;
                }));

        dashboard.setFailedCards(failedCards);
        dashboard.setStatus(status(dashboard, failedCards));
        return dashboard;
    }

    /** Runs one card; on any exception, logs it, records the card name and returns the ERROR card instead. */
    private static <T> T load(String cardName, List<String> failedCards, Supplier<T> loader, Supplier<T> errorCard) {
        try {
            return loader.get();
        } catch (RuntimeException e) {
            log.warn("Investor dashboard card {} could not be loaded", cardName, e);
            failedCards.add(cardName);
            return errorCard.get();
        }
    }

    /** ERROR if no card loaded, PARTIAL if some failed, EMPTY if every card is empty, OK otherwise. */
    private static String status(InvestorDashboardDTO dashboard, List<String> failedCards) {
        if (failedCards.size() == 4) {
            return InvestorDashboardDTO.STATUS_ERROR;
        }
        if (!failedCards.isEmpty()) {
            return InvestorDashboardDTO.STATUS_PARTIAL;
        }
        boolean allEmpty = InvestorDashboardDTO.STATUS_EMPTY.equals(dashboard.getValueEquityDebt().getStatus())
                && InvestorDashboardDTO.STATUS_EMPTY.equals(dashboard.getIncomeExpensesReturns().getStatus())
                && InvestorDashboardDTO.STATUS_EMPTY.equals(dashboard.getOccupancyExposure().getStatus())
                && InvestorDashboardDTO.STATUS_EMPTY.equals(dashboard.getOpportunities().getStatus());
        return allEmpty ? InvestorDashboardDTO.STATUS_EMPTY : InvestorDashboardDTO.STATUS_OK;
    }
}
