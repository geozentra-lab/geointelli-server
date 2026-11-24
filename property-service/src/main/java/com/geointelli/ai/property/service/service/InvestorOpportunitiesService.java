package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.InvestorOpportunitiesCardDTO;

/**
 * Builds the opportunities card of the investor dashboard (SCRUM-106) from the shared opportunity service of
 * SCRUM-76 ({@link OpportunityBrowseService}). The card is a bounded list of the best stored scores with their
 * ranking explanation and evidence coverage; nothing is rescored and nothing is written. Only the current user's
 * portfolios are read, to flag the properties the investor already holds.
 */
public interface InvestorOpportunitiesService {

    /** Items in the card when {@code limit} is omitted. */
    int DEFAULT_LIMIT = 5;
    /** Highest accepted {@code limit}; the card is a bounded list, not a browse. */
    int MAX_LIMIT = 20;

    /**
     * Reads the first {@code limit} scored properties, best first, from the shared opportunity service, optionally
     * restricted to a county or a state, and flags those held in the current user's portfolios (or in the one
     * requested). When the shared service has no candidate, the card is EMPTY, carries the service's own reason as
     * candidateStatus and lists no items.
     *
     * @param portfolioId one portfolio of the current user, or {@code null} for all of them
     * @param countyId    only properties in this county, or {@code null}
     * @param stateId     only properties in this state, or {@code null}
     * @param limit       number of items, 1 to {@link #MAX_LIMIT}; {@code null} means {@link #DEFAULT_LIMIT}
     * @return the card, with status OK or EMPTY
     * @throws IllegalArgumentException if portfolioId, countyId or stateId is not positive, or limit is out of range
     * @throws org.springframework.web.server.ResponseStatusException 404 if the portfolio is missing or belongs
     *         to another user
     */
    InvestorOpportunitiesCardDTO getCard(Long portfolioId, Long countyId, Long stateId, Integer limit);
}
