package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.InvestorValueEquityDebtCardDTO;

/**
 * Builds the value, equity and debt card of the investor dashboard (SCRUM-103) from the portfolio value
 * summaries of SCRUM-74. Only the current user's portfolios are read; nothing is changed.
 */
public interface InvestorValueEquityDebtService {

    /**
     * Adds up the market value, reported debt and equity of the current user's portfolios, or of one of them,
     * with the as-of date and the coverage of each amount. A complete total is given only when every holding is
     * covered; otherwise {@code total} is null and {@code knownTotal} is the partial sum. Without holdings, the
     * totals are 0 and the coverage percentages are null.
     *
     * @param portfolioId one portfolio of the current user, or {@code null} for all of them
     * @return the card, with status OK or EMPTY
     * @throws IllegalArgumentException if portfolioId is not positive
     * @throws org.springframework.web.server.ResponseStatusException 404 if the portfolio is missing or belongs
     *         to another user
     */
    InvestorValueEquityDebtCardDTO getCard(Long portfolioId);
}
