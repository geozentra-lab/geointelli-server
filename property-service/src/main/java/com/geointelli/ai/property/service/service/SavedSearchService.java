package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.PropertySearchResponse;
import com.geointelli.ai.property.service.dto.SavedSearchDTO;
import com.geointelli.ai.property.service.dto.SavedSearchPageDTO;
import com.geointelli.ai.property.service.dto.SavedSearchRequest;

/** Manages named query definitions owned by the current user. */
public interface SavedSearchService {
    /** Validates and stores a definition without running a property search. */
    SavedSearchDTO create(SavedSearchRequest request);
    /** Returns a bounded page of the user's saved definitions. */
    SavedSearchPageDTO list(int page, int size);
    /** Returns an owned definition, or 404 when absent or owned by another user. */
    SavedSearchDTO get(Long id);
    /** Replaces only supplied metadata/filters; pause affects monitoring settings, not manual replay. */
    SavedSearchDTO update(Long id, SavedSearchRequest request);
    /** Permanently removes the definition, monitoring settings and linked alert rules. Properties are preserved. */
    void delete(Long id);
    /** Runs the stored query against current data with caller-supplied pagination; rejects unsupported filter versions. */
    PropertySearchResponse results(Long id, int page, int size);
}
