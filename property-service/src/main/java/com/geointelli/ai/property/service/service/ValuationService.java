package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ValuationResponse;

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
}
