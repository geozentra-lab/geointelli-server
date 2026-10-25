package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.PropertySearchRequest;

/** Shared validation for direct property search, saved definitions and replay. */
public interface PropertySearchValidationService {
    /** Validates fields, ranges, geography, sorting and bounded pagination without executing a search. */
    void validate(PropertySearchRequest request);
}
