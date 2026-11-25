package com.geointelli.ai.property.service.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * JdbcTemplate implementation of {@link SchoolScoreReader}. The property IDs are passed as one array parameter, so a
 * bulk request of any size is one query per table, and the primary keys of both tables serve the lookups.
 */
@Repository
@RequiredArgsConstructor
public class JdbcSchoolScoreReader implements SchoolScoreReader {

    /** Resolved through the search path, like the queries below. */
    static final String TABLES_EXIST = """
        SELECT to_regclass('property_school_assignments') IS NOT NULL
           AND to_regclass('property_school_scores') IS NOT NULL""";

    /**
     * The grade shown is the one the score used; before the first score, the newest graded year. An UNMATCHED_ZONE row
     * has no school: its name is the one the zone gives.
     */
    static final String ASSIGNED_SCHOOLS = """
        SELECT a.property_id, a.level, a.school_id, coalesce(s.name, z.zone_school_name) AS name, s.sector, a.method,
            a.zone_source_type, a.distance_m, y.grade_year, g.letter_grade, g.fldoe_grade, g.pct_total_points,
            g.statewide_percentile
        FROM property_school_assignments a
        LEFT JOIN schools s ON s.id = a.school_id
        LEFT JOIN school_attendance_zones z ON z.id = a.zone_id AND a.school_id IS NULL
        LEFT JOIN property_school_scores sc ON sc.property_id = a.property_id
        CROSS JOIN LATERAL (SELECT coalesce(sc.grade_year, (SELECT max(school_year) FROM school_grades
                                                           WHERE statewide_percentile IS NOT NULL)) AS grade_year) y
        LEFT JOIN school_grades g ON g.state_school_id = s.state_school_id AND g.school_year = y.grade_year
        WHERE a.property_id = ANY (?)
        ORDER BY a.property_id, CASE a.level WHEN 'ELEMENTARY' THEN 1 WHEN 'MIDDLE' THEN 2 ELSE 3 END""";

    static final String SCORES = """
        SELECT property_id, quality_score, access_score, choice_score, school_score, label, grade_year, score_version,
            computed_at
        FROM property_school_scores
        WHERE property_id = ANY (?)""";

    private final JdbcTemplate jdbc;

    /** Set once the tables are found: a migration is never rolled back while the application runs. */
    private volatile boolean tablesFound;

    @Override
    public boolean available() {
        if (!tablesFound) tablesFound = Boolean.TRUE.equals(jdbc.queryForObject(TABLES_EXIST, Boolean.class));
        return tablesFound;
    }

    @Override
    public Map<Long, List<AssignedSchool>> assignedSchools(Collection<Long> propertyIds) {
        Map<Long, List<AssignedSchool>> result = new LinkedHashMap<>();
        if (propertyIds.isEmpty()) return result;
        jdbc.query(connection -> {
            var statement = connection.prepareStatement(ASSIGNED_SCHOOLS);
            statement.setArray(1, ids(connection, propertyIds));
            return statement;
        }, rs -> {
            AssignedSchool school = new AssignedSchool(rs.getLong("property_id"), rs.getString("level"),
                longOrNull(rs, "school_id"), rs.getString("name"), rs.getString("sector"), rs.getString("method"),
                rs.getString("zone_source_type"), rs.getBigDecimal("distance_m"), intOrNull(rs, "grade_year"),
                rs.getString("letter_grade"), rs.getString("fldoe_grade"), rs.getBigDecimal("pct_total_points"),
                intOrNull(rs, "statewide_percentile"));
            result.computeIfAbsent(school.propertyId(), id -> new ArrayList<>()).add(school);
        });
        return result;
    }

    @Override
    public Map<Long, Score> scores(Collection<Long> propertyIds) {
        Map<Long, Score> result = new LinkedHashMap<>();
        if (propertyIds.isEmpty()) return result;
        jdbc.query(connection -> {
            var statement = connection.prepareStatement(SCORES);
            statement.setArray(1, ids(connection, propertyIds));
            return statement;
        }, rs -> {
            Score score = new Score(rs.getLong("property_id"), rs.getBigDecimal("quality_score"),
                rs.getBigDecimal("access_score"), rs.getBigDecimal("choice_score"), rs.getBigDecimal("school_score"),
                rs.getString("label"), intOrNull(rs, "grade_year"), rs.getInt("score_version"),
                rs.getObject("computed_at", OffsetDateTime.class));
            result.put(score.propertyId(), score);
        });
        return result;
    }

    private static Array ids(java.sql.Connection connection, Collection<Long> propertyIds) throws SQLException {
        return connection.createArrayOf("bigint", propertyIds.toArray());
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
