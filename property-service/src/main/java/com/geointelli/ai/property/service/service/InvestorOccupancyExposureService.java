package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.InvestorOccupancyExposureCardDTO;

/**
 * Builds the occupancy and exposure card of the investor dashboard (SCRUM-105) from the portfolio exposure
 * summaries of SCRUM-74. Only the current user's portfolios are read; nothing is changed.
 */
public interface InvestorOccupancyExposureService {

    /**
     * Adds up the occupancy counts and the state and county exposure of the current user's portfolios, or of one
     * of them, and adds the coverage of known occupancy and known geography. Without holdings, the counts are 0,
     * the breakdowns are empty and the percentages are null.
     *
     * @param portfolioId one portfolio of the current user, or {@code null} for all of them
     * @return the card, with status OK or EMPTY
     * @throws IllegalArgumentException if portfolioId is not positive
     * @throws org.springframework.web.server.ResponseStatusException 404 if the portfolio is missing or belongs
     *         to another user
     */
    InvestorOccupancyExposureCardDTO getCard(Long portfolioId);
}
