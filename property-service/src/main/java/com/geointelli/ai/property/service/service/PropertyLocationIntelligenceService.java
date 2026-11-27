package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.LocationIntelligenceDTO;

/**
 * Location intelligence of a property (SCRUM-128 / SCRUM-53): composes the school (SCRUM-125), transport
 * (SCRUM-126) and risk (SCRUM-127) sections into one response. It only calls the section services and never reads
 * their data sources itself. The neighborhood section is not built yet and is not part of the response.
 */
public interface PropertyLocationIntelligenceService {

    /**
     * Returns the school, transport and risk sections of a property, each exactly as its own endpoint returns it.
     * The sections are read one after the other and independently: a section that fails is returned as null and
     * listed in {@link LocationIntelligenceDTO#getErrors()} with code
     * {@link LocationIntelligenceDTO#ERROR_SECTION_FAILED}, and a section that reports its own UNAVAILABLE status is
     * kept and listed with code {@link LocationIntelligenceDTO#ERROR_SECTION_UNAVAILABLE}; the other sections are
     * returned either way. Nothing is saved.
     *
     * @param propertyId internal ID of the property
     * @return the sections with status COMPLETE (every section read), PARTIAL (at least one section in errors) or
     *         UNAVAILABLE (no section could be read)
     * @throws IllegalArgumentException if {@code propertyId} is null or not positive
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException if no property has this ID
     */
    LocationIntelligenceDTO getLocationIntelligence(Long propertyId);
}
