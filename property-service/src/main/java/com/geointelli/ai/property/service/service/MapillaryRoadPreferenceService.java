package com.geointelli.ai.property.service.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Provides address-street preferences from a local road-centerline sample. */
public interface MapillaryRoadPreferenceService {
    /**
     * Matches the property address and compares the camera with nearby streets.
     * @param address property address
     * @param camera longitude/latitude array
     * @param targetLongitude parcel target longitude
     * @param targetLatitude parcel target latitude
     * @return match evidence and a preference priority; does not identify the entrance
     */
    Map<String, Object> assess(String address, JsonNode camera, double targetLongitude, double targetLatitude);
}
