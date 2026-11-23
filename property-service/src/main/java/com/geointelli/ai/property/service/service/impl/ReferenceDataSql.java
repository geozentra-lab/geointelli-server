package com.geointelli.ai.property.service.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.impl.ReferenceDataRows.Column;

/**
 * SQL of the reference loads, environmental (SCRUM-201) and school (SCRUM-202): a temporary staging table per file,
 * the COPY into it, the replacement of the same scope and the move into the target table. Table and column names are
 * constants of this class and {@link ReferenceDataRows}; every value is a bind parameter.
 */
public final class ReferenceDataSql {

    /** Flood polygons are cut into parts of at most this many vertices for fast point-in-polygon lookups. */
    public static final int SUBDIVIDE_MAX_VERTICES = 256;

    private ReferenceDataSql() {
    }

    /** Staging table of a dataset; dropped at commit. */
    public static String stagingTable(IngestionDataset dataset) {
        return "stage_" + targetTable(dataset);
    }

    /** Table a dataset loads into. */
    public static String targetTable(IngestionDataset dataset) {
        return switch (dataset) {
            case FLOOD_ZONES -> "flood_zones";
            case CENSUS_TRACTS -> "census_tracts";
            case NRI_TRACTS -> "nri_tract_risk";
            case STORM_EVENTS -> "storm_events";
            case SCHOOLS -> "schools";
            case SCHOOL_GRADES -> "school_grades";
            case SCHOOL_ZONES -> "school_attendance_zones";
            default -> throw new IllegalArgumentException("Not a reference dataset: " + dataset);
        };
    }

    public static String createStaging(IngestionDataset dataset, List<Column> columns) {
        return "CREATE TEMP TABLE " + stagingTable(dataset) + " ("
            + columns.stream().map(column -> column.name() + " " + column.sqlType()).collect(Collectors.joining(", "))
            + ") ON COMMIT DROP";
    }

    /** COPY of the normalised CSV (written with a header line) into the staging table. */
    public static String copy(IngestionDataset dataset, List<Column> columns) {
        return "COPY " + stagingTable(dataset) + " (" + names(columns) + ") FROM STDIN WITH (FORMAT csv, HEADER true)";
    }

    /** Serialises loads of the same scope (two jobs for one county would otherwise replace each other's rows). */
    public static String lock() {
        return "SELECT pg_advisory_xact_lock(hashtext(?))";
    }

    /** Duplicate keys in the staging table, reported before the insert fails on the primary key. */
    public static String duplicateKey(IngestionDataset dataset) {
        String key = switch (dataset) {
            case CENSUS_TRACTS, NRI_TRACTS -> "tract_fips";
            case STORM_EVENTS -> "event_id";
            case SCHOOLS -> "school_key";
            case SCHOOL_GRADES -> "(state_school_id || ' ' || school_year)";
            default -> null;
        };
        if (key == null) return null;
        return "SELECT " + key + "::text FROM " + stagingTable(dataset) + " GROUP BY " + key
            + " HAVING count(*) > 1 ORDER BY 1 LIMIT 1";
    }

    /** A state school id given to two schools of the file (it is unique among schools). */
    public static String duplicateStateSchoolId() {
        return "SELECT state_school_id FROM stage_schools WHERE state_school_id IS NOT NULL GROUP BY state_school_id "
            + "HAVING count(*) > 1 ORDER BY 1 LIMIT 1";
    }

    /**
     * School datasets (SCRUM-202): deletes the rows the file replaces. The scope is what the file contains: its
     * counties (schools, zones) or its school years (grades), so re-importing one county or one year touches nothing
     * else. Schools are upserted afterwards, so only the schools of those counties that left the file are deleted
     * here; their zones keep the row with school_id NULL (ON DELETE SET NULL).
     */
    public static String deleteSchoolScope(IngestionDataset dataset) {
        return switch (dataset) {
            case SCHOOLS -> """
                DELETE FROM schools t
                WHERE t.fips5 IN (SELECT DISTINCT fips5 FROM stage_schools)
                  AND NOT EXISTS (SELECT 1 FROM stage_schools s WHERE s.school_key = t.school_key)""";
            case SCHOOL_GRADES -> "DELETE FROM school_grades WHERE school_year IN (SELECT DISTINCT school_year FROM stage_school_grades)";
            case SCHOOL_ZONES -> "DELETE FROM school_attendance_zones "
                + "WHERE fips5 IN (SELECT DISTINCT fips5 FROM stage_school_attendance_zones)";
            default -> throw new IllegalArgumentException("Not a school dataset: " + dataset);
        };
    }

    /**
     * School datasets: what runs after the insert. Schools re-link every zone to its school by school_key (zones may
     * have been loaded first, and a reloaded school keeps its id). Grades rank pct_total_points within the same
     * FLDOE school type and year, for the years of the file; a school without pct_total_points or a letter grade,
     * or alone in its group, keeps statewide_percentile NULL.
     */
    public static String afterSchoolInsert(IngestionDataset dataset) {
        return switch (dataset) {
            case SCHOOLS -> """
                UPDATE school_attendance_zones z SET school_id = s.id
                FROM schools s
                WHERE s.school_key = z.school_key AND z.school_id IS DISTINCT FROM s.id""";
            case SCHOOL_GRADES -> """
                UPDATE school_grades g SET statewide_percentile = r.percentile
                FROM (
                    SELECT state_school_id, school_year,
                        CASE WHEN count(*) OVER (PARTITION BY school_year, fldoe_school_type) > 1
                            THEN round(100 * percent_rank() OVER (PARTITION BY school_year, fldoe_school_type
                                ORDER BY pct_total_points))::smallint END AS percentile
                    FROM school_grades
                    WHERE school_year IN (SELECT DISTINCT school_year FROM stage_school_grades)
                      AND pct_total_points IS NOT NULL AND fldoe_school_type IS NOT NULL AND letter_grade IS NOT NULL
                ) r
                WHERE g.state_school_id = r.state_school_id AND g.school_year = r.school_year""";
            case SCHOOL_ZONES -> null;
            default -> throw new IllegalArgumentException("Not a school dataset: " + dataset);
        };
    }

    /** Zones of the file's counties whose school_key names no loaded school (school_id NULL). */
    public static String unlinkedZones() {
        return "SELECT count(*) FROM school_attendance_zones WHERE fips5 IN "
            + "(SELECT DISTINCT fips5 FROM stage_school_attendance_zones) AND school_key IS NOT NULL AND school_id IS NULL";
    }

    /** Deletes the rows a new load of the scope replaces; the single parameter is the county or state FIPS. */
    public static String deleteScope(IngestionDataset dataset) {
        return switch (dataset) {
            case FLOOD_ZONES -> "DELETE FROM flood_zones WHERE fips5 = ?";
            case CENSUS_TRACTS -> "DELETE FROM census_tracts WHERE left(tract_fips, 2) = ?";
            case NRI_TRACTS -> "DELETE FROM nri_tract_risk WHERE left(tract_fips, 2) = ?";
            case STORM_EVENTS -> "DELETE FROM storm_events WHERE state_fips = ?";
            default -> throw new IllegalArgumentException("Not a reference dataset: " + dataset);
        };
    }

    /**
     * Moves the staging rows into the target table. Flood zones take the source file name as their parameter; each
     * polygon is repaired (ST_MakeValid), reduced to its polygons (ST_CollectionExtract 3) and subdivided, and a
     * polygon with no area left keeps one row with geom NULL (LEFT JOIN), so no source row is lost.
     */
    public static String insert(IngestionDataset dataset) {
        return switch (dataset) {
            case FLOOD_ZONES -> """
                INSERT INTO flood_zones (source_file, source_row, fips5, dfirm_id, fld_ar_id, fld_zone, zone_subty, sfha,
                    static_bfe, depth, velocity, len_unit, vel_unit, v_datum, dual_zone, study_typ, version_id, source_cit, geom)
                SELECT CAST(? AS varchar), s.source_row, s.fips5, s.dfirm_id, s.fld_ar_id, s.fld_zone, s.zone_subty, s.sfha,
                    s.static_bfe, s.depth, s.velocity, s.len_unit, s.vel_unit, s.v_datum, s.dual_zone, s.study_typ,
                    s.version_id, s.source_cit, part.geom
                FROM stage_flood_zones s
                LEFT JOIN LATERAL (
                    SELECT ST_Multi(d.geom)::geometry(MultiPolygon, 4326) AS geom
                    FROM ST_Subdivide(ST_CollectionExtract(ST_MakeValid(ST_GeomFromText(s.geom_wkt, 4326)), 3), %d) AS d(geom)
                    WHERE NOT ST_IsEmpty(d.geom)
                ) part ON true
                ORDER BY s.source_row""".formatted(SUBDIVIDE_MAX_VERTICES);
            case CENSUS_TRACTS -> """
                INSERT INTO census_tracts (tract_fips, fips5, name, land_area_m2, water_area_m2, geom)
                SELECT s.tract_fips, s.fips5, s.name, s.land_area_m2, s.water_area_m2,
                    ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_GeomFromText(s.geom_wkt, 4326)), 3))::geometry(MultiPolygon, 4326)
                FROM stage_census_tracts s
                ORDER BY s.source_row""";
            case NRI_TRACTS -> """
                INSERT INTO nri_tract_risk (tract_fips, fips5, nri_version, risk_score, risk_rating,
                    hrcn_score, hrcn_rating, ifld_score, ifld_rating, cfld_score, cfld_rating, trnd_score, trnd_rating,
                    wfir_score, wfir_rating, swnd_score, swnd_rating, ltng_score, ltng_rating, hwav_score, hwav_rating, raw)
                SELECT s.tract_fips, s.fips5, s.nri_version, s.risk_score, s.risk_rating,
                    s.hrcn_score, s.hrcn_rating, s.ifld_score, s.ifld_rating, s.cfld_score, s.cfld_rating,
                    s.trnd_score, s.trnd_rating, s.wfir_score, s.wfir_rating, s.swnd_score, s.swnd_rating,
                    s.ltng_score, s.ltng_rating, s.hwav_score, s.hwav_rating, s.raw
                FROM stage_nri_tract_risk s
                ORDER BY s.source_row""";
            case STORM_EVENTS -> """
                INSERT INTO storm_events (event_id, episode_id, state_fips, fips5, cz_type, cz_fips, cz_name, event_type,
                    begin_at, end_at, cz_timezone, injuries_direct, injuries_indirect, deaths_direct, deaths_indirect,
                    damage_property_usd, damage_crops_usd, magnitude, magnitude_type, tor_f_scale, flood_cause, geom)
                SELECT s.event_id, s.episode_id, s.state_fips, s.fips5, s.cz_type, s.cz_fips, s.cz_name, s.event_type,
                    s.begin_at, s.end_at, s.cz_timezone, s.injuries_direct, s.injuries_indirect, s.deaths_direct,
                    s.deaths_indirect, s.damage_property_usd, s.damage_crops_usd, s.magnitude, s.magnitude_type,
                    s.tor_f_scale, s.flood_cause,
                    CASE WHEN s.begin_lat IS NOT NULL AND s.begin_lon IS NOT NULL
                        THEN ST_SetSRID(ST_MakePoint(s.begin_lon, s.begin_lat), 4326) END
                FROM stage_storm_events s
                ORDER BY s.source_row""";
            // School datasets take the source file name as their parameter, like flood zones.
            case SCHOOLS -> """
                INSERT INTO schools (school_key, state_school_id, nces_id, fips5, county, name, sector, level, school_type,
                    lowest_grade, highest_grade, is_virtual, is_active, enrollment, free_reduced_lunch_pct, address, city, zip,
                    in_schools_zip, status_note, geom, source_file, source_row)
                SELECT s.school_key, s.state_school_id, s.nces_id, s.fips5, s.county, s.name, s.sector, s.level, s.school_type,
                    s.lowest_grade, s.highest_grade, s.is_virtual, s.is_active, s.enrollment, s.free_reduced_lunch_pct,
                    s.address, s.city, s.zip, s.in_schools_zip, s.status_note,
                    ST_SetSRID(ST_MakePoint(s.longitude, s.latitude), 4326), CAST(? AS varchar), s.source_row
                FROM stage_schools s
                ORDER BY s.source_row
                ON CONFLICT (school_key) DO UPDATE SET state_school_id = EXCLUDED.state_school_id,
                    nces_id = EXCLUDED.nces_id, fips5 = EXCLUDED.fips5, county = EXCLUDED.county, name = EXCLUDED.name,
                    sector = EXCLUDED.sector, level = EXCLUDED.level, school_type = EXCLUDED.school_type,
                    lowest_grade = EXCLUDED.lowest_grade, highest_grade = EXCLUDED.highest_grade,
                    is_virtual = EXCLUDED.is_virtual, is_active = EXCLUDED.is_active, enrollment = EXCLUDED.enrollment,
                    free_reduced_lunch_pct = EXCLUDED.free_reduced_lunch_pct, address = EXCLUDED.address,
                    city = EXCLUDED.city, zip = EXCLUDED.zip, in_schools_zip = EXCLUDED.in_schools_zip,
                    status_note = EXCLUDED.status_note, geom = EXCLUDED.geom, source_file = EXCLUDED.source_file,
                    source_row = EXCLUDED.source_row, loaded_at = CURRENT_TIMESTAMP""";
            case SCHOOL_GRADES -> """
                INSERT INTO school_grades (state_school_id, school_year, district_number, school_number, school_name,
                    letter_grade, fldoe_grade, is_baseline, fldoe_school_type, is_charter, is_title_i, alternative_ese_center,
                    %1$s, total_points_earned, total_components, pct_total_points, pct_tested, pct_econ_disadvantaged,
                    source_file, source_row)
                SELECT s.state_school_id, s.school_year, s.district_number, s.school_number, s.school_name,
                    s.letter_grade, s.fldoe_grade, s.is_baseline, s.fldoe_school_type, s.is_charter, s.is_title_i,
                    s.alternative_ese_center, %2$s, s.total_points_earned, s.total_components, s.pct_total_points,
                    s.pct_tested, s.pct_econ_disadvantaged, CAST(? AS varchar), s.source_row
                FROM stage_school_grades s
                ORDER BY s.source_row""".formatted(String.join(", ", ReferenceDataRows.GRADE_COMPONENTS),
                    ReferenceDataRows.GRADE_COMPONENTS.stream().map(c -> "s." + c).collect(Collectors.joining(", ")));
            case SCHOOL_ZONES -> """
                INSERT INTO school_attendance_zones (fips5, county, level, school_key, school_id, zone_school_name,
                    school_match, source_school_id, source, source_type, zone_year, method_fr, note_fr, geom, source_file, source_row)
                SELECT s.fips5, s.county, s.level, s.school_key, sc.id, s.zone_school_name, s.school_match,
                    s.source_school_id, s.source, s.source_type, s.zone_year, s.method_fr, s.note_fr,
                    ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_GeomFromText(s.geom_wkt, 4326)), 3))::geometry(MultiPolygon, 4326),
                    CAST(? AS varchar), s.source_row
                FROM stage_school_attendance_zones s
                LEFT JOIN schools sc ON sc.school_key = s.school_key
                ORDER BY s.source_row""";
            default -> throw new IllegalArgumentException("Not a reference dataset: " + dataset);
        };
    }

    /** Whether the insert takes the source file name as its parameter. */
    public static boolean insertTakesSourceFile(IngestionDataset dataset) {
        return dataset == IngestionDataset.FLOOD_ZONES || dataset.isSchoolData();
    }

    /**
     * What the load wrote: source rows stored, table rows written and rows without geometry. Flood zones are
     * counted for the county (parameter: fips5), the others for the state (parameter: state FIPS).
     */
    public static String loaded(IngestionDataset dataset) {
        return switch (dataset) {
            case FLOOD_ZONES -> "SELECT count(DISTINCT source_row) AS loaded, count(*) AS written, "
                + "count(*) FILTER (WHERE geom IS NULL) AS without_geometry FROM flood_zones WHERE fips5 = ?";
            case CENSUS_TRACTS -> "SELECT count(*) AS loaded, count(*) AS written, "
                + "count(*) FILTER (WHERE ST_IsEmpty(geom)) AS without_geometry FROM census_tracts WHERE left(tract_fips, 2) = ?";
            case NRI_TRACTS -> "SELECT count(*) AS loaded, count(*) AS written, 0 AS without_geometry "
                + "FROM nri_tract_risk WHERE left(tract_fips, 2) = ?";
            case STORM_EVENTS -> "SELECT count(*) AS loaded, count(*) AS written, "
                + "count(*) FILTER (WHERE geom IS NULL) AS without_geometry FROM storm_events WHERE state_fips = ?";
            // School datasets: counted for the counties or years of the file; no parameter.
            case SCHOOLS -> "SELECT count(*) AS loaded, count(*) AS written, 0 AS without_geometry FROM schools "
                + "WHERE fips5 IN (SELECT DISTINCT fips5 FROM stage_schools)";
            case SCHOOL_GRADES -> "SELECT count(*) AS loaded, count(*) AS written, 0 AS without_geometry FROM school_grades "
                + "WHERE school_year IN (SELECT DISTINCT school_year FROM stage_school_grades)";
            case SCHOOL_ZONES -> "SELECT count(*) AS loaded, count(*) AS written, "
                + "count(*) FILTER (WHERE ST_IsEmpty(geom)) AS without_geometry FROM school_attendance_zones "
                + "WHERE fips5 IN (SELECT DISTINCT fips5 FROM stage_school_attendance_zones)";
            default -> throw new IllegalArgumentException("Not a reference dataset: " + dataset);
        };
    }

    /** Bind parameters of {@link #loaded} and of the geometry check: the scope FIPS, or none for school datasets. */
    public static Object[] scopeParameters(IngestionDataset dataset, String scope) {
        return dataset.isSchoolData() ? new Object[0] : new Object[] {scope};
    }

    /** Stored polygons of the load that are not valid (they would make spatial lookups unreliable); null when none are checked. */
    public static String invalidGeometry(IngestionDataset dataset) {
        return switch (dataset) {
            case FLOOD_ZONES -> "SELECT count(*) FROM flood_zones WHERE fips5 = ? AND geom IS NOT NULL AND NOT ST_IsValid(geom)";
            case CENSUS_TRACTS -> "SELECT count(*) FROM census_tracts WHERE left(tract_fips, 2) = ? AND NOT ST_IsValid(geom)";
            case SCHOOL_ZONES -> "SELECT count(*) FROM school_attendance_zones WHERE fips5 IN "
                + "(SELECT DISTINCT fips5 FROM stage_school_attendance_zones) AND NOT ST_IsValid(geom)";
            default -> null;
        };
    }

    /** One row of load history (data vintage). */
    public static String recordLoad() {
        return "INSERT INTO reference_dataset_loads (dataset, source, downloaded_on, file, fips5, rows_read, rows_loaded, job_id) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

    /** Where each dataset comes from, recorded in the load history. */
    public static String source(IngestionDataset dataset) {
        return switch (dataset) {
            case FLOOD_ZONES -> "FEMA National Flood Hazard Layer (NFHL), layer 28 Flood Hazard Zones, "
                + "hazards.fema.gov/arcgis/rest/services/public/NFHL/MapServer/28";
            case CENSUS_TRACTS -> "US Census Bureau TIGER/Line 2024 census tracts (2020 tracts), www2.census.gov/geo/tiger/TIGER2024/TRACT";
            case NRI_TRACTS -> "FEMA National Risk Index, census tracts (NRI_Table_CensusTracts), www.fema.gov/about/openfema/data-sets/national-risk-index-data";
            case STORM_EVENTS -> "NOAA NCEI Storm Events Database, bulk CSV, www.ncei.noaa.gov/pub/data/swdi/stormevents/csvfiles";
            case SCHOOLS -> "District school GIS (schools.zip, corrected for 2026-27) and NCES Common Core of Data 2024-25 "
                + "public school directory, nces.ed.gov/ccd";
            case SCHOOL_GRADES -> "FLDOE School Grades 2026 (SchoolGrades26.xlsx, letter grades back to 1999), "
                + "www.fldoe.org/accountability/accountability-reporting/school-grades";
            case SCHOOL_ZONES -> "District attendance zone GIS (schools.zip), NCES SABS 2015-16 and Miami-Dade County GIS "
                + "M-DCPS elementary, middle and high attendance boundaries";
            default -> throw new IllegalArgumentException("Not a reference dataset: " + dataset);
        };
    }

    private static String names(List<Column> columns) {
        return columns.stream().map(Column::name).collect(Collectors.joining(", "));
    }
}
