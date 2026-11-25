package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The schools a family can choose instead of its assigned school, searched in memory within 3 miles: the choice
 * part of school score v2 (SCRUM-203). 3 miles, not 5: within 5 miles almost every Miami-Dade property has a
 * top-percentile school of each level (choice 95 on average, little spread), while within 2 miles a fifth of them has
 * no high school at all. Distances are great circles on the mean-radius sphere, the same as PostGIS
 * {@code ST_Distance(geography, geography, false)}; a grid of 0.1 degree cells limits each search to the nearby
 * schools. Searching the 1.7 million property-level pairs of Miami-Dade this way takes seconds, where the same
 * radius search in SQL took several minutes.
 */
final class SchoolChoiceIndex {

    static final double RADIUS_M = 3 * SchoolScoreV2.MILE_M;
    /** Mean radius of the WGS 84 ellipsoid, (2a + b) / 3, the sphere of PostGIS geography. */
    static final double EARTH_RADIUS_M = 6_371_008.7714;
    static final double CELL_DEGREES = 0.1;

    /** One school of one level with its statewide percentile, null when not graded. */
    record Candidate(String level, long schoolId, double lat, double lon, Integer percentile) { }

    private final Map<String, Map<Long, List<Candidate>>> cellsByLevel = new HashMap<>();

    SchoolChoiceIndex(List<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            cellsByLevel.computeIfAbsent(candidate.level(), level -> new HashMap<>())
                .computeIfAbsent(cell(row(candidate.lat()), column(candidate.lon())), key -> new ArrayList<>())
                .add(candidate);
        }
    }

    /**
     * Choice part of one level at a point: the best statewide percentile among the schools of that level within
     * 3 miles, other than the assigned school.
     *
     * @param excludedSchoolId the assigned school of the level, or null
     * @return the best percentile; 0 when there is no other school within 3 miles; null when there are some but none
     *         is graded, or the point is unknown
     */
    Double choice(String level, Double lat, Double lon, Long excludedSchoolId) {
        if (lat == null || lon == null) return null;
        Map<Long, List<Candidate>> cells = cellsByLevel.get(level);
        if (cells == null) return 0.0;
        double latSpan = Math.toDegrees(RADIUS_M / EARTH_RADIUS_M);
        double lonSpan = latSpan / Math.max(Math.cos(Math.toRadians(Math.abs(lat) + latSpan)), 1e-6);
        boolean found = false;
        Integer best = null;
        for (long r = row(lat - latSpan); r <= row(lat + latSpan); r++) {
            for (long c = column(lon - lonSpan); c <= column(lon + lonSpan); c++) {
                for (Candidate candidate : cells.getOrDefault(cell(r, c), List.of())) {
                    if (excludedSchoolId != null && candidate.schoolId() == excludedSchoolId) continue;
                    if (meters(lat, lon, candidate.lat(), candidate.lon()) > RADIUS_M) continue;
                    found = true;
                    if (candidate.percentile() != null && (best == null || candidate.percentile() > best)) {
                        best = candidate.percentile();
                    }
                }
            }
        }
        if (!found) return 0.0;
        return best == null ? null : best.doubleValue();
    }

    /** Great-circle distance in metres (haversine). */
    static double meters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dPhi / 2), 2) + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(dLambda / 2), 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private static long row(double lat) {
        return (long) Math.floor(lat / CELL_DEGREES);
    }

    private static long column(double lon) {
        return (long) Math.floor(lon / CELL_DEGREES);
    }

    private static long cell(long row, long column) {
        return row * 100_000L + column;
    }
}
