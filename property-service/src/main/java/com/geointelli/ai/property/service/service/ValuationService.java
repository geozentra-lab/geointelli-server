package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.dto.ComparableResponse;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;

/** Reads and explains monthly model results without recalculating or adjusting them. */
public interface ValuationService {
    /**
     * Returns the stored valuation with display rules and separately labelled supporting sales.
     *
     * @param propertyId positive internal property ID
     * @return supported/limited output, or unsupported with reasons when no model result is available
     * @throws IllegalArgumentException if the ID is not positive
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException if the property does not exist
     */
    ValuationResponse getValuation(Long propertyId);

    /**
     * Formats an archived model output without consulting current property facts or live comparables.
     *
     * @param snapshot frozen prediction fields reconstructed from history, not today's prediction row
     * @param supportingComparables comparables archived with that version, or null if none were retained
     * @return historical display with the same status and suppression rules as the current valuation
     * @throws IllegalArgumentException if the snapshot or its property ID is missing/invalid
     */
    ValuationResponse explainSnapshot(PropertyValuePrediction snapshot, ComparableResponse supportingComparables);
}
