package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.RiskContextDTO;

/**
 * Risk section of the property location intelligence (SCRUM-127 / SCRUM-53), read from the environmental
 * reference data loaded by SCRUM-201.
 */
public interface PropertyRiskContextService {

    /**
     * Returns the risk context of a property at the point on its parcel polygon: the FEMA flood zone
     * containing the point, the FEMA National Risk Index ratings of the census tract containing it, and the
     * NOAA storm events recorded within {@link RiskContextDTO#STORM_RADIUS_M} since
     * {@link RiskContextDTO#STORM_FROM_YEAR}. The three blocks are evaluated independently, each with its own
     * status, source and download date, so a source without data for the location never hides the others.
     * Missing evidence is reported as NO_DATA or NO_COVERAGE and never as a rating. Nothing is saved.
     *
     * @param propertyId internal ID of the property
     * @return the risk context with status AVAILABLE, NO_COVERAGE (the property has no usable parcel polygon)
     *         or UNAVAILABLE (the reference data could not be read; the error is returned in the section,
     *         not thrown)
     * @throws IllegalArgumentException if {@code propertyId} is null or not positive
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException if no property has this ID
     */
    RiskContextDTO getRiskContext(Long propertyId);
}
