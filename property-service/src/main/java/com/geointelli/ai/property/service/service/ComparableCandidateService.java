package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ComparableCandidateRequest;
import com.geointelli.ai.property.service.dto.ComparableCandidateResponse;

/** Retrieves sale candidates before similarity ranking and valuation. */
public interface ComparableCandidateService {
    /**
     * Finds bounded, recent sale candidates for an internal property ID.
     *
     * @param propertyId internal property database ID
     * @param request recency, distance and similarity limits
     * @return candidates, selection policy and data limitations
     */
    ComparableCandidateResponse findCandidates(Long propertyId, ComparableCandidateRequest request);
}
