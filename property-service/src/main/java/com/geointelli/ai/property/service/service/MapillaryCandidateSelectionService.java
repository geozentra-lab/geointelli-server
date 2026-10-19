package com.geointelli.ai.property.service.service;

import com.fasterxml.jackson.databind.JsonNode;

import com.geointelli.ai.property.service.integration.mapillary.MapillaryCandidateMatcher;

import java.util.List;

/** Orders geometrically eligible images for a bounded visual review. */
public interface MapillaryCandidateSelectionService {
    /**
     * Prioritizes recent captures and varied camera positions.
     * @param candidates geometrically eligible candidates
     * @return candidates in download order, including fallback nearby frames
     */
    List<MapillaryCandidateMatcher.Candidate> order(List<MapillaryCandidateMatcher.Candidate> candidates);

    /**
     * Requires capture metadata within a conservative 08:00-17:00 local solar-time window.
     * @param image API image metadata with capture time and computed coordinates
     * @return true when eligible for daytime-only selection
     */
    boolean isDaylightCapture(JsonNode image);

    /**
     * Returns a ranking heuristic, not a probability of correct property identity.
     * @param candidate image and geometric measurements
     * @return ranking score from zero to one hundred
     */
    double selectionScore(MapillaryCandidateMatcher.Candidate candidate);
}
