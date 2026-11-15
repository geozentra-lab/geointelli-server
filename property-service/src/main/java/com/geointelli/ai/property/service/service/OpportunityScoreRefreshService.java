package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.OpportunityRefreshResultDTO;

/**
 * Keeps the stored opportunity scores (SCRUM-76) up to date: scores every property that has a stored valuation
 * with {@link OpportunityScoringService} and stores the result, which {@code GET /api/opportunities} reads.
 * Only one run executes at a time in this instance; a run is idempotent, so concurrent instances only repeat work.
 */
public interface OpportunityScoreRefreshService {

    /** Which properties a run scores. */
    enum Mode {
        /** Properties without a stored score for the current version and valuation run. */
        INCREMENTAL,
        /** Every property with a stored valuation. */
        FULL
    }

    /**
     * Scores the selected properties in batches, each stored in its own transaction, then removes the scores of
     * properties that no longer have a valuation.
     *
     * @param mode INCREMENTAL or FULL
     * @return counts and timing of the run
     * @throws IllegalArgumentException if mode is null
     * @throws org.springframework.web.server.ResponseStatusException 409 if a run is already in progress
     */
    OpportunityRefreshResultDTO refresh(Mode mode);
}
