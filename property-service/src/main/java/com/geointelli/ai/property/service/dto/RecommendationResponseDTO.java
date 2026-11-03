package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Research suggestions with explicit evidence limitations, not an active-listing feed. */
public record RecommendationResponseDTO(
        Status status, Instant generatedAt, String source, Instant sourceAsOf,
        String rankingMethod, int limit, List<Long> appliedCountyIds,
        BigDecimal minBudget, BigDecimal maxBudget, List<String> investmentGoals,
        List<String> limitations, List<Item> content) {

    public enum Status {
        NO_PREFERENCES, LOCATION_REQUIRED, NO_MATCHES, PARTIAL, READY
    }

    /** Rank is response order; no investment, affordability or listing score is implied. */
    public record Item(int rank, PropertySearchItem property, List<String> reasons,
            String budgetMatch, String goalMatch, String listingStatus) {
    }
}
