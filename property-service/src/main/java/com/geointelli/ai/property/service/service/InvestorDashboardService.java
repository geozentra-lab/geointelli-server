package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.InvestorDashboardDTO;

import java.time.YearMonth;

/**
 * Composes the investor dashboard (SCRUM-107) from the four investor card services: value, equity and debt
 * (SCRUM-103), income, expenses and returns (SCRUM-104), occupancy and exposure (SCRUM-105) and opportunities
 * (SCRUM-106). Nothing is recalculated here and nothing is changed.
 */
public interface InvestorDashboardService {

    /**
     * Validates the portfolio scope and the period first, then loads every card for that scope. A card that fails
     * gets status ERROR and its name in {@code failedCards}; the cards that loaded are kept.
     *
     * @param portfolioId one portfolio of the current user, or {@code null} for all of them
     * @param fromMonth   first month of the income card, included; give both months or none
     * @param toMonth     last month of the income card, included; must be before the current month
     * @return the dashboard, with status OK, EMPTY, PARTIAL or ERROR
     * @throws IllegalArgumentException if portfolioId is not positive or the period is not valid (same rules as
     *         {@link InvestorIncomeExpensesReturnsService#resolvePeriod})
     * @throws org.springframework.web.server.ResponseStatusException 404 if the portfolio is missing or belongs
     *         to another user
     */
    InvestorDashboardDTO getDashboard(Long portfolioId, YearMonth fromMonth, YearMonth toMonth);
}
