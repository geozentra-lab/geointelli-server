package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.dto.PropertySearchRequest;
import com.geointelli.ai.property.service.dto.PropertySearchResponse;

/**
 * Searches properties by location, attributes and map area.
 */
public interface PropertySearchService {

    /**
     * Returns one page of properties matching every filter given in the request.
     *
     * <p>When a bounding box is given, only properties with a parcel geometry intersecting it are
     * returned; properties without parcel geometry are excluded rather than located approximately.
     * Lot size ranges are in square feet; properties without a lot size are excluded when a lot size
     * range is given.
     *
     * @param request filters, paging and sorting; unset filters are ignored
     * @return the requested page, sorted by the requested field then by id
     * @throws IllegalArgumentException if paging, sorting, a range or the bounding box is invalid
     */
    PropertySearchResponse search(PropertySearchRequest request);

    /** Returns matching IDs after the cursor, ordered by ID, without a count query; limit is 1 to 1000. */
    List<Long> findMatchingIds(PropertySearchRequest request, long afterPropertyId, int limit);
}
