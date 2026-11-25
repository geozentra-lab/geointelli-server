package com.geointelli.ai.property.service.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Reads the stored school assignments and school score v2 of properties (SCRUM-203, migration V49) for the API.
 * Nothing is computed here.
 */
public interface SchoolScoreReader {

    /**
     * One assigned school with its FLDOE grade of the property's grade year.
     *
     * @param schoolId null for UNMATCHED_ZONE: the zone's school is not in the school data
     * @param name the school's name; for UNMATCHED_ZONE, the name the zone gives (null once a zones reload removed
     *             the zone, until the county is assigned again)
     * @param gradeYear grade year of the property's score, or the newest graded year when it has no score yet
     * @param letterGrade A-F, null when FLDOE gave no letter that year
     * @param fldoeGrade FLDOE's code as published (A-F, I, N, P), null when the school is not listed that year
     */
    record AssignedSchool(long propertyId, String level, Long schoolId, String name, String sector, String method,
            String zoneSourceType, BigDecimal distanceM, Integer gradeYear, String letterGrade, String fldoeGrade,
            BigDecimal pctTotalPoints, Integer statewidePercentile) { }

    /** The stored school score v2 of one property; parts and score null when unknown. */
    record Score(long propertyId, BigDecimal quality, BigDecimal access, BigDecimal choice, BigDecimal school,
            String label, Integer gradeYear, int scoreVersion, OffsetDateTime computedAt) { }

    /** Whether migration V49 is applied; without it the properties simply have no assignment or score. */
    boolean available();

    /** Assigned schools by property, elementary, middle then high; properties without any are absent. */
    Map<Long, List<AssignedSchool>> assignedSchools(Collection<Long> propertyIds);

    /** Stored scores by property; properties without one are absent. */
    Map<Long, Score> scores(Collection<Long> propertyIds);
}
