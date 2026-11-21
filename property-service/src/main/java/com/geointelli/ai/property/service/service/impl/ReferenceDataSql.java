package com.geointelli.ai.property.service.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.impl.ReferenceDataRows.Column;

/**
 * SQL of the environmental reference loads (SCRUM-201): a temporary staging table per file, the COPY into it, the
 * replacement of the same scope and the move into the target table. Table and column names are constants of this
 * class and {@link ReferenceDataRows}; every value is a bind parameter.
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
            default -> null;
        };
        if (key == null) return null;
        return "SELECT " + key + "::text FROM " + stagingTable(dataset) + " GROUP BY " + key
            + " HAVING count(*) > 1 ORDER BY 1 LIMIT 1";
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
            default -> throw new IllegalArgumentException("Not a reference dataset: " + dataset);
        };
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
            default -> throw new IllegalArgumentException("Not a reference dataset: " + dataset);
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
            default -> throw new IllegalArgumentException("Not a reference dataset: " + dataset);
        };
    }

    private static String names(List<Column> columns) {
        return columns.stream().map(Column::name).collect(Collectors.joining(", "));
    }
}
