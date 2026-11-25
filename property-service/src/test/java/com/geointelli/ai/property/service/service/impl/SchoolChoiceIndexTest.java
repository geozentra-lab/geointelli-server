package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.geointelli.ai.property.service.service.impl.SchoolChoiceIndex.Candidate;

/** The choice part of school score v2: the best graded school within 3 miles other than the assigned one. */
class SchoolChoiceIndexTest {

    /** A point in Miami. */
    private static final double LAT = 25.75;
    private static final double LON = -80.22;
    /** Degrees of latitude per mile on the PostGIS sphere. */
    private static final double MILE_LAT = Math.toDegrees(SchoolScoreV2.MILE_M / SchoolChoiceIndex.EARTH_RADIUS_M);

    private static Candidate elementary(long id, double milesNorth, Integer percentile) {
        return new Candidate("ELEMENTARY", id, LAT + milesNorth * MILE_LAT, LON, percentile);
    }

    @Test
    @DisplayName("The best percentile within 3 miles wins, and a better school farther away does not count")
    void bestWithinRadius() {
        SchoolChoiceIndex index = new SchoolChoiceIndex(List.of(elementary(1, 1, 60), elementary(2, 2.9, 85),
            elementary(3, 3.1, 99)));

        assertEquals(85.0, index.choice("ELEMENTARY", LAT, LON, null));
    }

    @Test
    @DisplayName("The assigned school is not a choice")
    void excludesAssignedSchool() {
        SchoolChoiceIndex index = new SchoolChoiceIndex(List.of(elementary(1, 1, 60), elementary(2, 2, 85)));

        assertEquals(60.0, index.choice("ELEMENTARY", LAT, LON, 2L));
    }

    @Test
    @DisplayName("No other school within 3 miles is a known 0; schools that are all ungraded are unknown")
    void noneOrUngraded() {
        SchoolChoiceIndex index = new SchoolChoiceIndex(List.of(elementary(1, 0.5, null), elementary(2, 1, null),
            elementary(3, 20, 99)));

        assertNull(index.choice("ELEMENTARY", LAT, LON, null));
        assertEquals(0.0, index.choice("ELEMENTARY", LAT, LON - 1, null));
        // only the assigned school nearby
        assertEquals(0.0, new SchoolChoiceIndex(List.of(elementary(1, 0.5, 70))).choice("ELEMENTARY", LAT, LON, 1L));
    }

    @Test
    @DisplayName("Schools of another level are not choices, and an unknown point has no choice part")
    void otherLevelAndUnknownPoint() {
        SchoolChoiceIndex index = new SchoolChoiceIndex(List.of(new Candidate("HIGH", 9, LAT, LON, 90)));

        assertEquals(0.0, index.choice("ELEMENTARY", LAT, LON, null));
        assertEquals(90.0, index.choice("HIGH", LAT, LON, null));
        assertNull(index.choice("HIGH", null, LON, null));
    }

    @Test
    @DisplayName("Schools across a grid cell edge and east-west are found")
    void acrossCells() {
        // 2.5 miles east of a point just west of a 0.1 degree cell edge
        double lat = 25.75;
        double lon = -80.2001;
        double eastLon = lon + 2.5 * SchoolScoreV2.MILE_M / (SchoolChoiceIndex.EARTH_RADIUS_M * Math.cos(Math.toRadians(lat)))
            * 180 / Math.PI;
        SchoolChoiceIndex index = new SchoolChoiceIndex(List.of(new Candidate("MIDDLE", 4, lat, eastLon, 77)));

        assertEquals(77.0, index.choice("MIDDLE", lat, lon, null));
    }

    @Test
    @DisplayName("Distances are great circles on the PostGIS sphere")
    void greatCircle() {
        // one degree of latitude: pi * R / 180 = 111,195.08 m
        assertEquals(111_195.08, SchoolChoiceIndex.meters(25, -80, 26, -80), 0.01);
        assertEquals(0.0, SchoolChoiceIndex.meters(25.75, -80.22, 25.75, -80.22));
    }
}
