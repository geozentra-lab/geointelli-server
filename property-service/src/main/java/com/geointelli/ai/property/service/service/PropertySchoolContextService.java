package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.SchoolContextDTO;

/**
 * School section of the property location intelligence (SCRUM-125 / SCRUM-53).
 */
public interface PropertySchoolContextService {

    /**
     * Returns the school context of a property from its stored school analysis: distances to the nearest
     * public and charter schools, number of schools within 1 km, school score with its label and parts,
     * distance unit and source. The score uses the known values only. Nothing is saved.
     *
     * @param propertyId internal ID of the property
     * @return the school context with status AVAILABLE, NO_NEARBY_RESULTS (data exists but no school
     *         within 1 km) or NO_COVERAGE (no school data for the property)
     * @throws IllegalArgumentException if {@code propertyId} is null or not positive
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException if no property has this ID
     */
    SchoolContextDTO getSchoolContext(Long propertyId);
}
