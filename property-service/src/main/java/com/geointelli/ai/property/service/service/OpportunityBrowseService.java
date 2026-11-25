package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.OpportunityBrowseRequest;
import com.geointelli.ai.property.service.dto.OpportunityPageDTO;

/**
 * Browses scored investment opportunities (SCRUM-168). Reads the stored output of {@link OpportunityScoringService}
 * written by {@link OpportunityScoreRefreshService}; only scores of the current version with status SCORED are
 * listed. Property data is public, so no ownership applies. Nothing is written.
 */
public interface OpportunityBrowseService {

    int DEFAULT_SIZE = 20;
    int MAX_SIZE = 100;
    /**
     * Pages start within the first 10,000 results (page × size below this). Any investor may page, and an offset
     * costs a scan of every skipped row: 8 ms at 10,000 but 4 s at 400,000 on the local data. The total is still
     * reported; narrower filters reach the rest.
     */
    int MAX_RESULTS = 10_000;

    /** Supported orders; ties are always broken by property ID. */
    enum SortOrder {
        /** Highest score first (default). */
        SCORE_DESC,
        /** Highest one-year forecast growth first, then highest score. */
        FORECAST_GROWTH_1Y_DESC,
        /** Lowest estimated value first, then highest score. */
        VALUE_ASC,
        /** Highest estimated value first, then highest score. */
        VALUE_DESC
    }

    /**
     * One page of scored properties matching the filters.
     *
     * @param request page (page × size below {@link #MAX_RESULTS}), size (1 to 100), optional county, state,
     *                minimum score (0 to 100), estimated value range and sort
     * @return the page with each score's factors and coverage, and a status telling an empty page apart:
     *         NO_MATCHES, NO_ELIGIBLE_OPPORTUNITIES or NOT_SCORED
     * @throws IllegalArgumentException when a parameter is out of range or the sort is not supported
     */
    OpportunityPageDTO browse(OpportunityBrowseRequest request);
}
