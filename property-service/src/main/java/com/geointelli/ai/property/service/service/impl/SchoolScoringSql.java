package com.geointelli.ai.property.service.service.impl;

/**
 * SQL of the school assignment and of school score v2 (SCRUM-203), over the SCRUM-202 tables (V44) and the two
 * tables of V49. Every statement runs inside the caller's transaction; the temporary tables are dropped at commit.
 * Rules shared by every statement:
 * <ul>
 *   <li>the point of a property is {@code ST_PointOnSurface} of its largest parcel (a point always inside it);</li>
 *   <li>a school teaches a level when it offers at least two of the level's grades (elementary K-5, middle 6-8, high
 *       9-12), so a 5-8 school is a middle school and an 8-12 school a high school; without a grade range, its
 *       NCES level decides (a COMBINATION school teaches every level);</li>
 *   <li>distances are metres on the spheroid ({@code geography}).</li>
 * </ul>
 */
final class SchoolScoringSql {

    private SchoolScoringSql() {
    }

    /** The three levels with their grade range (kindergarten = 0). */
    static final String LEVELS = "(VALUES ('ELEMENTARY', 0, 5), ('MIDDLE', 6, 8), ('HIGH', 9, 12)) lv(level, lo, hi)";

    /** Whether school {@code s} teaches level {@code lv}. LEAST and GREATEST skip NULLs, hence the explicit case. */
    static final String TEACHES = """
        CASE WHEN s.lowest_grade IS NULL OR s.highest_grade IS NULL THEN s.level IN (lv.level, 'COMBINATION')
             ELSE LEAST(s.highest_grade, lv.hi) - GREATEST(s.lowest_grade, lv.lo) >= 1 END""";

    /** Serialises the assignment and the scoring of one county (two jobs would otherwise replace each other's rows). */
    static final String LOCK = "SELECT pg_advisory_xact_lock(hashtext(?))";

    static final String COUNTY_ID = "SELECT id FROM counties WHERE fips_code = ?";

    /** Counties with attendance zones and properties, which an assignment covers; optionally one county. */
    static final String COUNTIES_TO_ASSIGN = """
        SELECT c.id, c.fips_code, c.name, st.code
        FROM counties c JOIN states st ON st.id = c.state_id
        WHERE c.fips_code IS NOT NULL AND (CAST(? AS VARCHAR) IS NULL OR c.fips_code = ?)
          AND EXISTS (SELECT 1 FROM school_attendance_zones z WHERE z.fips5 = c.fips_code)
          AND EXISTS (SELECT 1 FROM properties p WHERE p.county_id = c.id)
        ORDER BY c.fips_code""";

    /** Counties with stored assignments, which a score v2 run covers; optionally one county. */
    static final String COUNTIES_TO_SCORE = """
        SELECT c.id, c.fips_code, c.name, st.code
        FROM counties c JOIN states st ON st.id = c.state_id
        WHERE c.fips_code IS NOT NULL AND (CAST(? AS VARCHAR) IS NULL OR c.fips_code = ?)
          AND EXISTS (SELECT 1 FROM properties p JOIN property_school_assignments a ON a.property_id = p.id
                      WHERE p.county_id = c.id)
        ORDER BY c.fips_code""";

    static final String COUNT_TO_ASSIGN = """
        SELECT count(DISTINCT pa.property_id)
        FROM parcels pa JOIN properties p ON p.id = pa.property_id
        WHERE p.county_id = ? AND pa.geom IS NOT NULL AND NOT ST_IsEmpty(pa.geom)""";

    static final String COUNT_TO_SCORE = """
        SELECT count(DISTINCT a.property_id)
        FROM property_school_assignments a JOIN properties p ON p.id = a.property_id
        WHERE p.county_id = ?""";

    // ---- assignment ---------------------------------------------------------------------------------------------

    /** Temporary tables of the assignment; pg_temp so a permanent table of the same name is never touched. */
    static final String DROP_ASSIGNMENT_TABLES = "DROP TABLE IF EXISTS pg_temp.school_points, "
        + "pg_temp.school_zone_levels, pg_temp.new_school_assignments, pg_temp.school_nearest";

    // The temporary tables are declared, then filled with INSERT ... SELECT: a bind parameter works there, while a
    // CREATE TABLE AS is a utility statement.
    static final String CREATE_POINTS = """
        CREATE TEMP TABLE school_points (property_id BIGINT NOT NULL, pt geometry(Point, 4326) NOT NULL)
        ON COMMIT DROP""";

    /** One point per property of the county with a parcel. */
    static final String INSERT_POINTS = """
        INSERT INTO school_points (property_id, pt)
        SELECT DISTINCT ON (pa.property_id) pa.property_id, ST_PointOnSurface(pa.geom)
        FROM parcels pa
        JOIN properties p ON p.id = pa.property_id
        WHERE p.county_id = ? AND pa.geom IS NOT NULL AND NOT ST_IsEmpty(pa.geom)
        ORDER BY pa.property_id, ST_Area(pa.geom) DESC, pa.fid""";
    static final String INDEX_POINTS = "CREATE INDEX ON school_points USING GIST (pt)";

    /**
     * The zones of the county that can assign a level, a zone of that level or a COMBINATION zone: those of an active
     * school that teaches the level, and those without a school (their school is not in the school data; it cannot be
     * checked, and the zone still says the property belongs to a school we do not have). A closed school's zone is
     * out of date and skipped. The rank of the source (OFFICIAL first) and then the smaller zone break ties between
     * zones. The area is planar (square degrees): zones that overlap lie at the same latitude, so it orders them as
     * their true area does, while a geodesic area of a detailed polygon costs tens of milliseconds.
     */
    static final String CREATE_ZONE_LEVELS = """
        CREATE TEMP TABLE school_zone_levels (zone_id BIGINT NOT NULL, level VARCHAR(12) NOT NULL,
            school_id BIGINT, source_type VARCHAR(30) NOT NULL, source_rank INTEGER, area DOUBLE PRECISION,
            geom geometry(MultiPolygon, 4326) NOT NULL) ON COMMIT DROP""";
    static final String INSERT_ZONE_LEVELS = """
        INSERT INTO school_zone_levels (zone_id, level, school_id, source_type, source_rank, area, geom)
        SELECT z.id, lv.level, z.school_id, z.source_type,
            array_position(ARRAY['OFFICIAL', 'OFFICIAL_RECONSTRUCTED', 'COUNTY_WIDE_SINGLE_SCHOOL', 'NCES_SABS_2015_16',
                'UNOFFICIAL']::text[], z.source_type::text) AS source_rank,
            ST_Area(z.geom) AS area, z.geom
        FROM school_attendance_zones z
        LEFT JOIN schools s ON s.id = z.school_id
        JOIN %s ON z.level = lv.level OR z.level = 'COMBINATION'
        WHERE z.fips5 = ? AND (z.school_id IS NULL OR (s.is_active AND %s))""".formatted(LEVELS, TEACHES);

    static final String CREATE_NEW_ASSIGNMENTS = """
        CREATE TEMP TABLE new_school_assignments (
            property_id BIGINT NOT NULL, level VARCHAR(12) NOT NULL, school_id BIGINT,
            method VARCHAR(14) NOT NULL, zone_id BIGINT, zone_source_type VARCHAR(30), distance_m NUMERIC(10, 1),
            PRIMARY KEY (property_id, level)) ON COMMIT DROP""";

    /**
     * Methods ZONE and UNMATCHED_ZONE: per property and level, the best zone containing the point; when that zone has
     * no school, the level gets no school and no distance (never a guessed school). The loop runs over the zones and
     * finds the points with the GIST index, so each zone polygon is prepared once for all its points.
     */
    static final String INSERT_ZONE_MATCHES = """
        INSERT INTO new_school_assignments (property_id, level, school_id, method, zone_id, zone_source_type, distance_m)
        SELECT m.property_id, m.level, m.school_id, CASE WHEN m.school_id IS NULL THEN 'UNMATCHED_ZONE' ELSE 'ZONE' END,
            m.zone_id, m.source_type, round(ST_Distance(m.pt::geography, s.geom::geography)::numeric, 1)
        FROM (
            SELECT DISTINCT ON (pt.property_id, zl.level) pt.property_id, pt.pt, zl.level, zl.school_id, zl.zone_id,
                zl.source_type
            FROM school_zone_levels zl
            JOIN school_points pt ON ST_Intersects(zl.geom, pt.pt)
            ORDER BY pt.property_id, zl.level, zl.source_rank, zl.area, zl.zone_id) m
        LEFT JOIN schools s ON s.id = m.school_id""";

    /**
     * Schools that method NEAREST may assign: active, regular, in-person public schools of the county that teach the
     * level and have an attendance zone of that level (so a magnet school without a zone is never assigned), or any
     * such school when the county has no zone of that level.
     */
    static final String CREATE_NEAREST_SCHOOLS = """
        CREATE TEMP TABLE school_nearest (level VARCHAR(12) NOT NULL, school_id BIGINT NOT NULL,
            geog geography(Point, 4326) NOT NULL) ON COMMIT DROP""";
    static final String INSERT_NEAREST_SCHOOLS = """
        INSERT INTO school_nearest (level, school_id, geog)
        SELECT lv.level, s.id, s.geom::geography
        FROM schools s
        JOIN %s ON %s
        WHERE s.fips5 = ? AND s.is_active AND s.sector = 'PUBLIC' AND s.school_type = 'REGULAR'
          AND NOT coalesce(s.is_virtual, false)
          AND (EXISTS (SELECT 1 FROM school_zone_levels zl WHERE zl.school_id = s.id AND zl.level = lv.level)
               OR NOT EXISTS (SELECT 1 FROM school_zone_levels zl WHERE zl.level = lv.level))""".formatted(LEVELS, TEACHES);

    /** Method NEAREST, for the levels no zone contains: the nearest school of {@link #CREATE_NEAREST_SCHOOLS}. */
    static final String INSERT_NEAREST = """
        INSERT INTO new_school_assignments (property_id, level, school_id, method, zone_id, zone_source_type, distance_m)
        SELECT g.property_id, g.level, n.school_id, 'NEAREST', NULL, NULL, round(n.meters::numeric, 1)
        FROM (
            SELECT pt.property_id, pt.pt::geography AS geog, lv.level
            FROM school_points pt CROSS JOIN (VALUES ('ELEMENTARY'), ('MIDDLE'), ('HIGH')) lv(level)
            WHERE NOT EXISTS (SELECT 1 FROM new_school_assignments a
                              WHERE a.property_id = pt.property_id AND a.level = lv.level)) g
        CROSS JOIN LATERAL (
            SELECT sn.school_id, ST_Distance(sn.geog, g.geog) AS meters
            FROM school_nearest sn WHERE sn.level = g.level
            ORDER BY sn.geog <-> g.geog, sn.school_id
            LIMIT 1) n""";

    /** Rows of the county's properties that the new assignment no longer has (a lost parcel or school). */
    static final String DELETE_VANISHED_ASSIGNMENTS = """
        DELETE FROM property_school_assignments t
        USING properties p
        WHERE p.id = t.property_id AND p.county_id = ?
          AND NOT EXISTS (SELECT 1 FROM new_school_assignments n
                          WHERE n.property_id = t.property_id AND n.level = t.level)""";

    /** Rows whose school, method, zone or distance changed; unchanged rows are not written at all. */
    static final String UPDATE_CHANGED_ASSIGNMENTS = """
        UPDATE property_school_assignments t
        SET school_id = n.school_id, method = n.method, zone_id = n.zone_id, zone_source_type = n.zone_source_type,
            distance_m = n.distance_m, assigned_at = now()
        FROM new_school_assignments n
        WHERE n.property_id = t.property_id AND n.level = t.level
          AND (t.school_id, t.method, t.zone_id, t.zone_source_type, t.distance_m)
              IS DISTINCT FROM (n.school_id, n.method, n.zone_id, n.zone_source_type, n.distance_m)""";

    static final String INSERT_NEW_ASSIGNMENTS = """
        INSERT INTO property_school_assignments (property_id, level, school_id, method, zone_id, zone_source_type,
            distance_m, assigned_at)
        SELECT n.property_id, n.level, n.school_id, n.method, n.zone_id, n.zone_source_type, n.distance_m, now()
        FROM new_school_assignments n
        WHERE NOT EXISTS (SELECT 1 FROM property_school_assignments t
                          WHERE t.property_id = n.property_id AND t.level = n.level)""";

    static final String COUNT_POINTS = "SELECT count(*) FROM school_points";
    /** Properties with a school at one level at least (UNMATCHED_ZONE rows have none). */
    static final String COUNT_ASSIGNED_PROPERTIES = """
        SELECT count(DISTINCT property_id) FROM new_school_assignments WHERE school_id IS NOT NULL""";
    static final String ASSIGNMENT_SUMMARY = """
        SELECT level, method, count(*) AS n FROM new_school_assignments GROUP BY level, method""";

    // ---- score v2 -----------------------------------------------------------------------------------------------

    /** The FLDOE year the quality and choice parts use: the newest year with statewide percentiles. */
    static final String GRADE_YEAR = "SELECT max(school_year) FROM school_grades WHERE statewide_percentile IS NOT NULL";

    /**
     * The schools a family can choose instead of its zoned school, statewide: active, in-person, regular public or
     * charter schools, with the level they teach and their statewide percentile of the grade year (NULL when not
     * graded). Searched within 3 miles by {@link SchoolChoiceIndex}.
     */
    static final String CHOICE_SCHOOLS = """
        SELECT lv.level, s.id AS school_id, ST_Y(s.geom) AS lat, ST_X(s.geom) AS lon, g.statewide_percentile AS percentile
        FROM schools s
        JOIN %s ON %s
        LEFT JOIN school_grades g ON g.state_school_id = s.state_school_id AND g.school_year = ?
        WHERE s.is_active AND s.sector IN ('PUBLIC', 'CHARTER') AND s.school_type = 'REGULAR'
          AND NOT coalesce(s.is_virtual, false)""".formatted(LEVELS, TEACHES);

    /**
     * One row per assigned property of the county: its point and, per level, the assigned school, the distance and
     * the school's statewide percentile of the grade year (all NULL for an UNMATCHED_ZONE level, so the score leaves
     * that level out of quality and access).
     */
    static final String CREATE_SCORE_INPUTS = """
        CREATE TEMP TABLE school_score_inputs (property_id BIGINT PRIMARY KEY, lat DOUBLE PRECISION,
            lon DOUBLE PRECISION, elementary_school_id BIGINT, elementary_distance_m NUMERIC(10, 1),
            elementary_percentile SMALLINT, middle_school_id BIGINT, middle_distance_m NUMERIC(10, 1),
            middle_percentile SMALLINT, high_school_id BIGINT, high_distance_m NUMERIC(10, 1), high_percentile SMALLINT)
        ON COMMIT DROP""";
    static final String INSERT_SCORE_INPUTS = """
        INSERT INTO school_score_inputs
        WITH pts AS (
            SELECT DISTINCT ON (pa.property_id) pa.property_id, ST_PointOnSurface(pa.geom) AS pt
            FROM parcels pa
            JOIN properties p ON p.id = pa.property_id
            WHERE p.county_id = ? AND pa.geom IS NOT NULL AND NOT ST_IsEmpty(pa.geom)
            ORDER BY pa.property_id, ST_Area(pa.geom) DESC, pa.fid)
        SELECT a.property_id, ST_Y(pts.pt) AS lat, ST_X(pts.pt) AS lon,
            max(a.school_id) FILTER (WHERE a.level = 'ELEMENTARY') AS elementary_school_id,
            max(a.distance_m) FILTER (WHERE a.level = 'ELEMENTARY') AS elementary_distance_m,
            max(g.statewide_percentile) FILTER (WHERE a.level = 'ELEMENTARY') AS elementary_percentile,
            max(a.school_id) FILTER (WHERE a.level = 'MIDDLE') AS middle_school_id,
            max(a.distance_m) FILTER (WHERE a.level = 'MIDDLE') AS middle_distance_m,
            max(g.statewide_percentile) FILTER (WHERE a.level = 'MIDDLE') AS middle_percentile,
            max(a.school_id) FILTER (WHERE a.level = 'HIGH') AS high_school_id,
            max(a.distance_m) FILTER (WHERE a.level = 'HIGH') AS high_distance_m,
            max(g.statewide_percentile) FILTER (WHERE a.level = 'HIGH') AS high_percentile
        FROM property_school_assignments a
        JOIN properties p ON p.id = a.property_id
        LEFT JOIN schools s ON s.id = a.school_id
        LEFT JOIN school_grades g ON g.state_school_id = s.state_school_id AND g.school_year = ?
        LEFT JOIN pts ON pts.property_id = a.property_id
        WHERE p.county_id = ?
        GROUP BY a.property_id, pts.pt""";
    static final String DROP_SCORE_TABLES = "DROP TABLE IF EXISTS pg_temp.school_score_inputs, pg_temp.new_school_scores";

    /** One page of the inputs, by property ID. */
    static final String SCORE_INPUT_PAGE = """
        SELECT * FROM school_score_inputs WHERE property_id > ? ORDER BY property_id LIMIT ?""";

    static final String CREATE_NEW_SCORES = """
        CREATE TEMP TABLE new_school_scores (
            property_id BIGINT PRIMARY KEY, quality_score NUMERIC(5, 2), access_score NUMERIC(5, 2),
            choice_score NUMERIC(5, 2), school_score NUMERIC(5, 2), label VARCHAR(10)) ON COMMIT DROP""";
    static final String COPY_NEW_SCORES = """
        COPY new_school_scores (property_id, quality_score, access_score, choice_score, school_score, label)
        FROM STDIN WITH (FORMAT csv)""";

    static final String DELETE_VANISHED_SCORES = """
        DELETE FROM property_school_scores t
        USING properties p
        WHERE p.id = t.property_id AND p.county_id = ?
          AND NOT EXISTS (SELECT 1 FROM new_school_scores n WHERE n.property_id = t.property_id)""";

    /** Parameters: grade year, score version (twice each: SET, then the comparison). */
    static final String UPDATE_CHANGED_SCORES = """
        UPDATE property_school_scores t
        SET quality_score = n.quality_score, access_score = n.access_score, choice_score = n.choice_score,
            school_score = n.school_score, label = n.label, grade_year = CAST(? AS SMALLINT),
            score_version = CAST(? AS SMALLINT), computed_at = now()
        FROM new_school_scores n
        WHERE n.property_id = t.property_id
          AND (t.quality_score, t.access_score, t.choice_score, t.school_score, t.label, t.grade_year, t.score_version)
              IS DISTINCT FROM (n.quality_score, n.access_score, n.choice_score, n.school_score, n.label,
                                CAST(? AS SMALLINT), CAST(? AS SMALLINT))""";

    /** Parameters: grade year, score version. */
    static final String INSERT_NEW_SCORES = """
        INSERT INTO property_school_scores (property_id, quality_score, access_score, choice_score, school_score, label,
            grade_year, score_version, computed_at)
        SELECT n.property_id, n.quality_score, n.access_score, n.choice_score, n.school_score, n.label,
            CAST(? AS SMALLINT), CAST(? AS SMALLINT), now()
        FROM new_school_scores n
        WHERE NOT EXISTS (SELECT 1 FROM property_school_scores t WHERE t.property_id = n.property_id)""";
}
