package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.ValuationHistoryEntry;

/** Reads archived valuations without triggering scoring or substituting current facts. */
public interface ValuationHistoryService {
    /**
     * Returns a bounded page of stored valuation versions.
     *
     * @param propertyId positive internal property ID
     * @param page zero-based page number
     * @param size page size, from one to one hundred
     * @return historical output formatted using the standard valuation display rules
     * @throws IllegalArgumentException if an ID or page parameter is invalid
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException if the property does not exist
     */
    PageResponse<ValuationHistoryEntry> getHistory(Long propertyId, int page, int size);
}
