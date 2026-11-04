package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.TransportContextDTO;

/**
 * Transport section of the property location intelligence (SCRUM-126 / SCRUM-53).
 */
public interface PropertyTransportContextService {

    /**
     * Returns the transport context of a property from its stored transportation analysis: distance to the
     * nearest access point of each supported, normalized category (RAIL, PEOPLE_MOVER, BUS, HIGHWAY), number of
     * bus stops within 500 m and transportation score (over the known values only) with its label, distance unit
     * and source. Nothing is saved.
     *
     * @param propertyId internal ID of the property
     * @return the transport context with status AVAILABLE, NO_COVERAGE (no transport data for the property)
     *         or UNAVAILABLE (the transport data source failed; the error is returned in the section, not thrown)
     * @throws IllegalArgumentException if {@code propertyId} is null or not positive
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException if no property has this ID
     */
    TransportContextDTO getTransportContext(Long propertyId);
}
