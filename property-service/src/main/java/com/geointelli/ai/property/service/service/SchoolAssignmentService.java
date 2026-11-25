package com.geointelli.ai.property.service.service;

import java.util.List;

/**
 * Assigns to every property with a parcel the school it is zoned for, per level (SCRUM-203), from the school
 * reference data of SCRUM-202: the attendance zone containing the property (ZONE; UNMATCHED_ZONE, without a school,
 * when the zone's school is not in the school data), or the nearest zoned public school of the county when no zone
 * contains it (NEAREST). The result is stored in {@code property_school_assignments} (migration V49).
 */
public interface SchoolAssignmentService {

    String METHOD_ZONE = "ZONE";
    String METHOD_UNMATCHED_ZONE = "UNMATCHED_ZONE";
    String METHOD_NEAREST = "NEAREST";

    /** Lock key prefix shared with the school score v2, so one county is never assigned and scored at once. */
    String LOCK_PREFIX = "school-scoring:";

    /** A county the school scoring can cover. */
    record SchoolCounty(Long countyId, String fips5, String name, String state) { }

    /** Number of assignment rows of one level and method. */
    record LevelCount(String level, String method, int rows) { }

    /**
     * What one county's assignment produced and wrote.
     *
     * @param properties properties of the county with a parcel
     * @param assignedProperties properties that got a school at one level at least
     * @param counts rows per level and method (ZONE, UNMATCHED_ZONE or NEAREST)
     * @param inserted rows added, {@code updated} rows changed, {@code deleted} rows removed; unchanged rows are not
     *                 written
     * @param millis duration of the assignment
     */
    record AssignmentResult(String fips5, int properties, int assignedProperties, List<LevelCount> counts,
            int inserted, int updated, int deleted, long millis) {

        /** Rows of one level and method, 0 when there are none. */
        public int rows(String level, String method) {
            return counts.stream().filter(c -> c.level().equals(level) && c.method().equals(method))
                .mapToInt(LevelCount::rows).sum();
        }

        /**
         * Short text for the job step, for example
         * {@code E 577570/1239/2946 M 578757/0/2998 H 578947/0/2808 (zone/unmatched/nearest)}.
         */
        public String summary() {
            StringBuilder text = new StringBuilder();
            for (String level : List.of("ELEMENTARY", "MIDDLE", "HIGH")) {
                text.append(level.charAt(0)).append(' ').append(rows(level, METHOD_ZONE)).append('/')
                    .append(rows(level, METHOD_UNMATCHED_ZONE)).append('/').append(rows(level, METHOD_NEAREST))
                    .append(' ');
            }
            return text.append("(zone/unmatched/nearest)").toString();
        }
    }

    /**
     * Counties an assignment covers: counties with attendance zones and properties.
     *
     * @param fips5 one county (5-digit FIPS code), or null for every such county
     * @return the counties, by FIPS code; empty when none qualifies
     */
    List<SchoolCounty> countiesToAssign(String fips5);

    /** Whether a county has this 5-digit FIPS code ({@code counties.fips_code}, migration V42). */
    boolean countyExists(String fips5);

    /**
     * Number of properties of the county with a parcel, which an assignment covers.
     *
     * @param county a county returned by {@link #countiesToAssign(String)}
     */
    int countPropertiesToAssign(SchoolCounty county);

    /**
     * Assigns the schools of one county inside the caller's transaction. Per property and level, the best zone that
     * contains the property's point (best source first, then the smallest zone), among the zones of an active school
     * teaching that level and the zones without a school: the zone's school (ZONE), or no school when the zone has none
     * (UNMATCHED_ZONE, left out of the score). When no such zone contains the point, the nearest active, regular,
     * in-person public school of the county that has a zone of that level (NEAREST).
     * Only the rows that change are written; rows the new assignment no longer has are deleted.
     *
     * @param fips5 5-digit FIPS code of the county
     * @return what was assigned and written
     * @throws IllegalStateException when called outside a transaction
     * @throws IllegalArgumentException when no county has this FIPS code
     */
    AssignmentResult assignCounty(String fips5);
}
