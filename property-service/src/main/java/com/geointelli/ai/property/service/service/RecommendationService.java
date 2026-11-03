package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.RecommendationResponseDTO;

/** Provides property research suggestions for the authenticated user's saved preferences. */
public interface RecommendationService {
    int DEFAULT_LIMIT = 20;
    int MAX_LIMIT = 50;

    /**
     * Matches saved counties and explains which other preferences cannot be evaluated.
     * All county matches have equal relevance; property ID resolves ties deterministically.
     *
     * @param limit maximum number of suggestions, from one to fifty
     * @return suggestions or an explicit cold-start/empty status
     * @throws IllegalArgumentException if the limit is invalid
     */
    RecommendationResponseDTO recommend(int limit);
}
