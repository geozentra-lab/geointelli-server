package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestmentAnalysisRequest;
import com.geointelli.ai.property.service.dto.InvestmentAnalysisResponse;
import com.geointelli.ai.property.service.dto.InvestmentFinancingRequest;
import com.geointelli.ai.property.service.dto.InvestmentOperatingRequest;
import com.geointelli.ai.property.service.dto.InvestorIncomeExpensesReturnsCardDTO;
import com.geointelli.ai.property.service.dto.InvestorIncomeExpensesReturnsCardDTO.Actual;
import com.geointelli.ai.property.service.dto.InvestorIncomeExpensesReturnsCardDTO.Period;
import com.geointelli.ai.property.service.dto.InvestorIncomeExpensesReturnsCardDTO.Projected;
import com.geointelli.ai.property.service.dto.PortfolioFinancialCategoryDTO;
import com.geointelli.ai.property.service.dto.PortfolioFinancialSummaryDTO;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import com.geointelli.ai.property.service.entity.FinancialRecordType;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.repository.HoldingRentFacts;
import com.geointelli.ai.property.service.repository.PortfolioHoldingRepository;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.InvestmentAnalysisService;
import com.geointelli.ai.property.service.service.InvestorIncomeExpensesReturnsService;
import com.geointelli.ai.property.service.service.PortfolioFinancialSummaryService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Actual figures: the SCRUM-74 financial summary of each included portfolio, added up. Projected figures: one
 * SCRUM-64 investment calculation on the current contract rent and purchase price of the rented holdings, and the
 * contract rent of the same months, each holding counted only while it was held and leased. The two sections are
 * never mixed: recorded costs are not used in the projection.
 */
@Service
public class InvestorIncomeExpensesReturnsServiceImpl implements InvestorIncomeExpensesReturnsService {

    static final int MAX_PORTFOLIOS = 100;
    static final YearMonth MIN_MONTH = YearMonth.of(1900, 1);
    static final String NO_RENT_REASON = "No rented holding has a monthly contract rent";
    static final String NO_HOLDINGS_REASON = "No holdings";
    static final String NOI_INPUTS = "Holdings store no vacancy rate or operating costs: projected net operating "
            + "income is not calculated.";
    static final String NOTE = "actual = recorded cash of the period (SCRUM-74 financial summaries), after the "
            + "current ownership share; a category without records is a missing cost, not a cost of 0. "
            + "projected = contract rent of the rented holdings for the same months, each holding only while it was "
            + "held and leased, through the SCRUM-64 investment calculation. The two sections are never added "
            + "together.";

    private final CurrentUserService currentUserService;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final PortfolioFinancialSummaryService portfolioFinancialSummaryService;
    private final InvestmentAnalysisService investmentAnalysisService;
    private final Clock clock;

    public InvestorIncomeExpensesReturnsServiceImpl(CurrentUserService currentUserService,
                                                    PortfolioRepository portfolioRepository,
                                                    PortfolioHoldingRepository portfolioHoldingRepository,
                                                    PortfolioFinancialSummaryService portfolioFinancialSummaryService,
                                                    InvestmentAnalysisService investmentAnalysisService,
                                                    @Qualifier("portfolioSummaryClock") Clock clock) {
        this.currentUserService = currentUserService;
        this.portfolioRepository = portfolioRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.portfolioFinancialSummaryService = portfolioFinancialSummaryService;
        this.investmentAnalysisService = investmentAnalysisService;
        this.clock = clock;
    }

    /** {@inheritDoc} */
    @Override
    public InvestorIncomeExpensesReturnsCardDTO getCard(Long portfolioId, YearMonth fromMonth, YearMonth toMonth) {
        if (portfolioId != null && portfolioId <= 0) {
            throw new IllegalArgumentException("portfolioId must be a positive number");
        }
        Instant generatedAt = clock.instant();
        Period period = period(fromMonth, toMonth, YearMonth.from(generatedAt.atZone(ZoneOffset.UTC)));
        Long userId = currentUserService.getCurrentUserEntity().getId();

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

        // Actual: add up the SCRUM-74 financial summaries over the period
        List<PortfolioFinancialSummaryDTO> summaries = new ArrayList<>();
        for (Long id : portfolioIds) {
            summaries.add(portfolioFinancialSummaryService.summarize(id, userId, period.fromDate(), period.toDate()));
        }
        long totalHoldings = summaries.stream().mapToLong(PortfolioFinancialSummaryDTO::totalHoldings).sum();
        Actual actual = actual(summaries, totalHoldings);

        // Projected: contract rent of the rented holdings over the same months
        List<HoldingRentFacts> holdings = portfolioIds.isEmpty()
                ? List.of() : portfolioHoldingRepository.rentFacts(portfolioIds, userId);
        Projected projected = projected(holdings, period);

        InvestorIncomeExpensesReturnsCardDTO card = new InvestorIncomeExpensesReturnsCardDTO();
        card.setScope(portfolioId == null ? InvestorIncomeExpensesReturnsCardDTO.SCOPE_ALL_PORTFOLIOS
                : InvestorIncomeExpensesReturnsCardDTO.SCOPE_PORTFOLIO);
        card.setPortfolioId(portfolioId);
        card.setPortfolioCount(portfolioIds.size());
        card.setStatus(totalHoldings == 0
                ? InvestorIncomeExpensesReturnsCardDTO.STATUS_EMPTY : InvestorIncomeExpensesReturnsCardDTO.STATUS_OK);
        card.setGeneratedAt(generatedAt);
        card.setCurrency("USD");
        card.setPeriod(period);
        card.setTotalHoldings(totalHoldings);
        card.setActual(actual);
        card.setProjected(projected);
        card.setNote(NOTE);
        return card;
    }

    /** Both months or none (the last 12 complete months); only complete past months, at most MAX_MONTHS. */
    private static Period period(YearMonth fromMonth, YearMonth toMonth, YearMonth currentMonth) {
        if ((fromMonth == null) != (toMonth == null)) {
            throw new IllegalArgumentException("fromMonth and toMonth must be given together");
        }
        if (fromMonth == null) {
            toMonth = currentMonth.minusMonths(1);
            fromMonth = toMonth.minusMonths(11);
        }
        if (fromMonth.isAfter(toMonth)) {
            throw new IllegalArgumentException("fromMonth must not be after toMonth");
        }
        if (!toMonth.isBefore(currentMonth)) {
            throw new IllegalArgumentException("toMonth must be a complete month before " + currentMonth);
        }
        if (fromMonth.isBefore(MIN_MONTH)) {
            throw new IllegalArgumentException("fromMonth must not be before " + MIN_MONTH);
        }
        int months = (int) ChronoUnit.MONTHS.between(fromMonth, toMonth) + 1;
        if (months > MAX_MONTHS) {
            throw new IllegalArgumentException("The period must not be longer than " + MAX_MONTHS + " months");
        }
        return new Period(fromMonth, toMonth, fromMonth.atDay(1), toMonth.atEndOfMonth(), months,
                InvestorIncomeExpensesReturnsCardDTO.TIME_ZONE);
    }

    /** Sums the summaries; categories in enum order; expense categories without records are listed. */
    private static Actual actual(List<PortfolioFinancialSummaryDTO> summaries, long totalHoldings) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        long recordCount = 0;
        long holdingsWithRecords = 0;
        LocalDateTime updatedAt = null;
        Map<FinancialRecordCategory, BigDecimal> amounts = new EnumMap<>(FinancialRecordCategory.class);
        Map<FinancialRecordCategory, Long> counts = new EnumMap<>(FinancialRecordCategory.class);
        for (PortfolioFinancialSummaryDTO summary : summaries) {
            income = income.add(summary.income());
            expenses = expenses.add(summary.expenses());
            recordCount += summary.recordCount();
            holdingsWithRecords += summary.holdingsWithRecords();
            if (summary.recordsUpdatedAt() != null
                    && (updatedAt == null || summary.recordsUpdatedAt().isAfter(updatedAt))) {
                updatedAt = summary.recordsUpdatedAt();
            }
            for (PortfolioFinancialCategoryDTO category : summary.categories()) {
                amounts.merge(category.category(), category.amount(), BigDecimal::add);
                counts.merge(category.category(), category.recordCount(), Long::sum);
            }
        }
        List<PortfolioFinancialCategoryDTO> categories = new ArrayList<>();
        List<FinancialRecordCategory> missingCosts = new ArrayList<>();
        for (FinancialRecordCategory category : FinancialRecordCategory.values()) {
            if (amounts.containsKey(category)) {
                categories.add(new PortfolioFinancialCategoryDTO(category, category.getType(),
                        money(amounts.get(category)), counts.get(category)));
            } else if (category.getType() == FinancialRecordType.EXPENSE) {
                missingCosts.add(category);
            }
        }
        return new Actual(InvestorIncomeExpensesReturnsCardDTO.ACTUAL_BASIS,
                recordCount == 0 ? InvestorIncomeExpensesReturnsCardDTO.ACTUAL_NO_RECORDS
                        : InvestorIncomeExpensesReturnsCardDTO.ACTUAL_RECORDED,
                money(income), money(expenses), money(income.subtract(expenses)), recordCount,
                holdingsWithRecords, totalHoldings - holdingsWithRecords, List.copyOf(categories),
                List.copyOf(missingCosts), updatedAt);
    }

    /**
     * One SCRUM-64 calculation on the summed current contract rent (and purchase price when every rented holding has
     * one). The period rent counts each holding only for the days of the period it was held and leased.
     */
    private Projected projected(List<HoldingRentFacts> holdings, Period period) {
        long rented = 0;
        long withRent = 0;
        long withoutPrice = 0;
        long withoutStart = 0;
        long outsidePeriod = 0;
        BigDecimal monthlyRent = BigDecimal.ZERO;
        BigDecimal purchasePrice = BigDecimal.ZERO;
        BigDecimal periodRent = BigDecimal.ZERO;
        for (HoldingRentFacts holding : holdings) {
            if (holding.occupancy() != HoldingOccupancyStatus.RENTED) {
                continue;
            }
            rented++;
            if (holding.monthlyRent() == null || holding.monthlyRent().signum() <= 0) {
                continue;
            }
            withRent++;
            BigDecimal rent = money(byShare(holding.monthlyRent(), holding.share()));
            monthlyRent = monthlyRent.add(rent);
            if (holding.acquisitionDate() == null && holding.leaseStartDate() == null) {
                withoutStart++;
            }
            BigDecimal heldRent = periodRent(rent, holding.acquisitionDate(), holding.leaseStartDate(),
                    holding.leaseEndDate(), period.fromDate(), period.toDate());
            if (heldRent.signum() == 0) {
                outsidePeriod++;
            }
            periodRent = periodRent.add(heldRent);
            if (holding.purchasePrice() == null || holding.purchasePrice().signum() <= 0) {
                withoutPrice++;
            } else {
                purchasePrice = purchasePrice.add(money(byShare(holding.purchasePrice(), holding.share())));
            }
        }

        List<String> missing = new ArrayList<>();
        if (rented > withRent) {
            missing.add((rented - withRent) + " rented holding(s) without a monthly contract rent: not projected.");
        }
        if (withRent == 0) {
            String reason = holdings.isEmpty() ? NO_HOLDINGS_REASON : NO_RENT_REASON;
            return new Projected(InvestorIncomeExpensesReturnsCardDTO.PROJECTED_BASIS,
                    InvestorIncomeExpensesReturnsCardDTO.UNAVAILABLE, rented, 0, null, null, null, null, null,
                    List.copyOf(missing), reason);
        }
        if (withoutPrice > 0) {
            missing.add(withoutPrice + " rented holding(s) without a purchase price: gross yield unavailable.");
        }
        if (withoutStart > 0) {
            missing.add(withoutStart + " rented holding(s) without an acquisition date or lease start date: rent "
                    + "counted for the whole period.");
        }
        if (outsidePeriod > 0) {
            missing.add(outsidePeriod + " rented holding(s) not held or leased during the period: no rent counted "
                    + "for them.");
        }
        missing.add(NOI_INPUTS);

        // Canonical calculation (SCRUM-64): only the inputs the holdings really have
        InvestmentAnalysisResponse analysis = investmentAnalysisService.analyze(new InvestmentAnalysisRequest(
                new InvestmentOperatingRequest(monthlyRent, null, null, null, null, null, null, null),
                new InvestmentFinancingRequest(withoutPrice == 0 ? purchasePrice : null, null, null, null, null),
                null));
        missing.addAll(analysis.operating().notes());

        BigDecimal monthly = money(analysis.operating().monthlyGrossRent());
        return new Projected(InvestorIncomeExpensesReturnsCardDTO.PROJECTED_BASIS,
                InvestorIncomeExpensesReturnsCardDTO.AVAILABLE, rented, withRent, monthly,
                money(analysis.operating().annualGrossRent()), money(periodRent),
                withoutPrice == 0 ? analysis.grossYieldPercent() : null, null, List.copyOf(missing), null);
    }

    /**
     * Contract rent of one holding for the days of [from, to] on or after its acquisition date and inside its lease
     * (a missing date does not limit it). A fully covered month counts the whole monthly rent, a partly covered month
     * in proportion to its days.
     */
    static BigDecimal periodRent(BigDecimal monthlyRent, LocalDate acquisitionDate, LocalDate leaseStartDate,
                                 LocalDate leaseEndDate, LocalDate from, LocalDate to) {
        LocalDate start = from;
        if (acquisitionDate != null && acquisitionDate.isAfter(start)) {
            start = acquisitionDate;
        }
        if (leaseStartDate != null && leaseStartDate.isAfter(start)) {
            start = leaseStartDate;
        }
        LocalDate end = leaseEndDate != null && leaseEndDate.isBefore(to) ? leaseEndDate : to;
        BigDecimal total = BigDecimal.ZERO;
        if (start.isAfter(end)) {
            return total;
        }
        for (YearMonth month = YearMonth.from(start); !month.isAfter(YearMonth.from(end)); month = month.plusMonths(1)) {
            LocalDate first = start.isAfter(month.atDay(1)) ? start : month.atDay(1);
            LocalDate last = end.isBefore(month.atEndOfMonth()) ? end : month.atEndOfMonth();
            long days = ChronoUnit.DAYS.between(first, last) + 1;
            total = total.add(days == month.lengthOfMonth() ? monthlyRent
                    : monthlyRent.multiply(BigDecimal.valueOf(days))
                            .divide(BigDecimal.valueOf(month.lengthOfMonth()), 10, RoundingMode.HALF_UP));
        }
        return total;
    }

    /** Full-property amount x ownership share %. */
    private static BigDecimal byShare(BigDecimal amount, BigDecimal sharePercent) {
        return amount.multiply(sharePercent).movePointLeft(2);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
