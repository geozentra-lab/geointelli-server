package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ComparableCandidateRequest;
import com.geointelli.ai.property.service.dto.ComparableResponse;

/** Coordinates candidate retrieval and ranking for the comparables endpoint. */
public interface ComparableService {
    /**
     * Returns the requested number of explained sales from the bounded candidate pool.
     *
     * @param propertyId internal subject property ID, not folio
     * @param request date, distance, similarity and response-size limits
     * @return ranked sales with evidence limitations and insufficient-data status
     */
    ComparableResponse findComparables(Long propertyId, ComparableCandidateRequest request);
}
