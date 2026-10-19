package com.geointelli.ai.property.service.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Checks potential intervening parcels without claiming actual building visibility. */
public interface MapillarySightlineService {
    /**
     * Checks a camera-to-target ray against the available neighboring parcel sample.
     * @param parcelReview subject and bounded neighboring geometry evidence
     * @param camera longitude/latitude array
     * @param longitude target longitude
     * @param latitude target latitude
     * @return sightline decision and any intervening property IDs
     */
    Map<String, Object> assess(Map<String, Object> parcelReview, JsonNode camera, double longitude, double latitude);
}
