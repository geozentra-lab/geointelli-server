package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Evidence the opportunity score is calculated from (SCRUM-167): the property's location and its stored
 * ZentraValue valuation (SCRUM-58). Every value except {@code propertyId} may be null when it is not stored.
 *
 * @param propertyId          the property
 * @param countyId            county of the property, or null
 * @param stateId             state of that county, or null
 * @param hasValuation        whether a stored valuation exists for the property
 * @param valuationStatus     stored valuation status: supported, limited or unsupported
 * @param estimatedValue      current ZentraValue estimate
 * @param forecast1y          one-year forecast value
 * @param forecast3y          three-year forecast value
 * @param marketOutlook1yPct  one-year market outlook of the property's area, in percent
 * @param confidence          valuation confidence label: high, medium or low
 * @param confidenceScore     valuation confidence score, 0 to 100
 * @param modelVersion        version of the valuation model
 * @param dataThrough         last date of the data the valuation was built from
 * @param valuationRunId      run that produced the valuation
 */
public record OpportunitySignals(
        Long propertyId,
        Long countyId,
        Long stateId,
        boolean hasValuation,
        String valuationStatus,
        BigDecimal estimatedValue,
        BigDecimal forecast1y,
        BigDecimal forecast3y,
        Double marketOutlook1yPct,
        String confidence,
        Integer confidenceScore,
        String modelVersion,
        LocalDate dataThrough,
        String valuationRunId
) {
    /** Signals of a property that has no stored valuation. */
    public static OpportunitySignals withoutValuation(Long propertyId, Long countyId, Long stateId) {
        return new OpportunitySignals(propertyId, countyId, stateId, false, null, null, null, null, null, null,
                null, null, null, null);
    }
}
