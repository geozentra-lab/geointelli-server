package com.geointelli.ai.property.service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.geointelli.ai.property.service.dto.ValuationHistorySnapshot;

/**
 * Read-only boundary for Python publications. Implementations filter by property and order by
 * loaded_at DESC, id DESC, preserving multiple publications on the same valuation date. Never read current predictions
 * as a substitute for missing history or reconstruct earlier values from forecasts.
 */
public interface ValuationHistoryReader {
    /**
     * Reads frozen versions and their total count from the agreed history store.
     *
     * @param propertyId internal property ID; must be included in the storage query
     * @param pageable validated page/size limits
     * @return actual stored history, empty only when an available store contains no matching versions
     */
    Page<ValuationHistorySnapshot> findByPropertyId(Long propertyId, Pageable pageable);
}
