package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.InvestorIncomeExpensesReturnsCardDTO;

import java.time.YearMonth;

/**
 * Builds the income, expenses and returns card of the investor dashboard (SCRUM-104). The actual figures come from
 * the portfolio financial summaries of SCRUM-74 and the projected figures from the investment calculation of
 * SCRUM-64. Only the current user's portfolios are read; nothing is changed.
 */
public interface InvestorIncomeExpensesReturnsService {
    /** Longest accepted period, in months. */
    int MAX_MONTHS = 120;

    /**
     * Adds up the recorded income and expenses of the current user's portfolios, or of one of them, over whole
     * months, and projects the contract rent of the rented holdings over the same months. Missing costs and inputs
     * are listed, never counted as zero. Without months, the last 12 complete months are used.
     *
     * @param portfolioId one portfolio of the current user, or {@code null} for all of them
     * @param fromMonth   first month, included; give both months or none
     * @param toMonth     last month, included; must be before the current month
     * @return the card, with status OK or EMPTY
     * @throws IllegalArgumentException if portfolioId is not positive, only one month is given, fromMonth is after
     *         toMonth, toMonth is not a complete past month, fromMonth is before 1900-01 or the period is longer
     *         than {@link #MAX_MONTHS} months
     * @throws org.springframework.web.server.ResponseStatusException 404 if the portfolio is missing or belongs
     *         to another user
     */
    InvestorIncomeExpensesReturnsCardDTO getCard(Long portfolioId, YearMonth fromMonth, YearMonth toMonth);
}
