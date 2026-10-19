package com.geointelli.ai.property.service.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/** Provides read-only parcel association evidence for image review. */
public interface MapillaryParcelReviewService {
    /**
     * Reads parcel and neighboring-property evidence; does not certify the photographed house.
     * @param connection read-only database connection
     * @param propertyId internal property ID
     * @return geometry, address and association checks for manual review
     * @throws SQLException if the evidence cannot be read
     */
    Map<String, Object> inspect(Connection connection, long propertyId) throws SQLException;
}
