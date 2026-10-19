package com.geointelli.ai.property.service.service;

import java.nio.file.Path;
import java.sql.Connection;
import com.geointelli.ai.property.service.dto.MapillarySelectionDTO;

/** Finds a property-facing image using the existing Java selection rules. */
public interface MapillaryImageSelectionService {
    /**
     * Searches, screens and ranks a bounded set of nearby panoramas.
     * @param connection database connection for parcel geometry checks
     * @param propertyId property to locate
     * @param directory temporary working directory
     * @return selected image, or null when no suitable view is found
     * @throws Exception when database, network or image processing fails
     */
    MapillarySelectionDTO select(Connection connection, long propertyId, Path directory) throws Exception;
}
