package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.LenderCollateralAnalysisDTO.Geography;

/**
 * Geographic risk context of a lender collateral (SCRUM-181): the location of the property and the risk section of
 * SCRUM-127, with the sources and the missing coverage. No risk score is computed and nothing is saved.
 */
public interface LenderCollateralGeographyService {

    /**
     * Describes where the property is and what each risk source (FEMA flood zones, FEMA National Risk Index, NOAA
     * storm events) says about that place. A risk context that cannot be read gives an UNAVAILABLE geography with
     * the location kept; it is never thrown, so the collateral value and the LTV are always returned.
     *
     * @param propertyId the collateral property, positive
     * @return AVAILABLE when every source has evidence, PARTIAL when some do, NO_COVERAGE when none covers the
     *         location, UNAVAILABLE when the property is not found or the risk context cannot be read
     */
    Geography describe(Long propertyId);
}
