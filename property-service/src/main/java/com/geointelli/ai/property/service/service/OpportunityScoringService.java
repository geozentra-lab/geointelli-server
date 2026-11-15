package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.OpportunityScoreDTO;
import com.geointelli.ai.property.service.dto.OpportunitySignals;

import java.time.Instant;
import java.util.List;

/**
 * Calculates and explains opportunity scores (SCRUM-167) from the stored ZentraValue valuation of a property
 * (SCRUM-58). The rules are versioned ({@link #SCORE_VERSION}) and documented in
 * {@code docs/opportunity-scores.md}. Every factor is reported with its evidence or the reason it is
 * unavailable; unavailable factors add no points, and when essential evidence (a supported valuation and its
 * one-year forecast) is missing the overall score is unavailable instead of a misleading number.
 * Pure calculation: nothing is written.
 */
public interface OpportunityScoringService {

    /** Version of the scoring rules; recorded with every stored and returned score. */
    String SCORE_VERSION = "opportunity-v1";

    /** Highest possible score: the factor weights add up to this. */
    int MAX_SCORE = 100;

    /** How to read a score; returned with every score and page. */
    List<String> NOTES = List.of(
            "Version " + SCORE_VERSION + " scores the stored ZentraValue valuation and market forecasts; rent, financing "
                    + "and listing-price evidence is not stored yet, so those factors are unavailable.",
            "Unavailable factors add no points and are never treated as zero costs: missing data cannot raise a score. "
                    + "coveragePercent is the highest score the available evidence allows.",
            "Forecasts are model trend scenarios, not guarantees, and no listing price is known: the score ranks "
                    + "evidence for research and is not a recommendation to buy.");

    /**
     * Scores a property from its current stored valuation.
     *
     * @param propertyId the property
     * @return the score, its factors and coverage; status UNAVAILABLE (with reasons) when essential evidence is missing
     * @throws IllegalArgumentException if the ID is not positive
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException if the property does not exist
     */
    OpportunityScoreDTO getScore(Long propertyId);

    /**
     * Scores the given evidence. Used by {@link #getScore(Long)} and by the batch refresh, so both apply the same rules.
     *
     * @param signals  the property's stored evidence
     * @param scoredAt the calculation time to record
     * @return the score, its factors and coverage
     */
    OpportunityScoreDTO score(OpportunitySignals signals, Instant scoredAt);
}
