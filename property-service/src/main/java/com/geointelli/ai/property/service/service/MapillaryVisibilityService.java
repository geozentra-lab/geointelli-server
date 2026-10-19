package com.geointelli.ai.property.service.service;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** Provides image visibility heuristics for review ranking, not semantic house recognition. */
public interface MapillaryVisibilityService {
    /**
     * Measures potential obstructions in the center of a property-facing image.
     * @param image native-resolution candidate image
     * @return measurements, warnings and a visibility heuristic score
     */
    Map<String, Object> assess(BufferedImage image);

    /**
     * Orders evaluated candidates and marks the requested number for review.
     * @param candidates mutable candidate metadata
     * @param limit maximum review selections
     * @return number selected; never automatically approves a property image
     */
    int rank(List<Map<String, Object>> candidates, int limit);
}
