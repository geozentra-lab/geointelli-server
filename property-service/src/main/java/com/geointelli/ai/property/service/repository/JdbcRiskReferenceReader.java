package com.geointelli.ai.property.service.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * JdbcTemplate implementation of {@link RiskReferenceReader} over the SCRUM-201 tables (migration V42).
 * The property point is {@code ST_PointOnSurface(parcels.geom)}, the convention of the comparable search and
 * the street-view matcher. Polygon lookups use {@code ST_Intersects} on the GIST indexes of V42 so a point on
 * the shared edge of two polygon parts is still found; the storm radius uses the geography index of V42.
 */
@Repository
@RequiredArgsConstructor
public class JdbcRiskReferenceReader implements RiskReferenceReader {

    private static final String POINT = "ST_SetSRID(ST_MakePoint(?, ?), 4326)";

    private static final String PARCEL_POINT = """
            SELECT c.fips_code, ST_X(p.pt) AS lon, ST_Y(p.pt) AS lat
            FROM (SELECT ST_PointOnSurface(pa.geom) AS pt, pr.county_id
                  FROM parcels pa
                  JOIN properties pr ON pr.id = pa.property_id
                  WHERE pa.property_id = ?
                    AND pa.geom IS NOT NULL AND NOT ST_IsEmpty(pa.geom)
                    AND ST_SRID(pa.geom) = 4326 AND ST_IsValid(pa.geom)
                  ORDER BY pa.fid
                  LIMIT 1) p
            LEFT JOIN counties c ON c.id = p.county_id
            """;

    private static final String LOADED_ON = """
            SELECT MAX(downloaded_on) FROM reference_dataset_loads
            WHERE dataset = ? AND fips5 IS NOT DISTINCT FROM CAST(? AS CHAR(5))
            """;

    private static final String FLOOD_LOADED = "SELECT EXISTS (SELECT 1 FROM flood_zones WHERE fips5 = ?)";

    /** Parts of one FEMA polygon share (source_file, source_row); they are merged back into one row. */
    private static final String FLOOD_AT = """
            SELECT fld_zone, zone_subty, sfha, static_bfe, depth, study_typ, dfirm_id
            FROM flood_zones
            WHERE fips5 = ? AND geom IS NOT NULL AND ST_Intersects(geom, %s)
            GROUP BY source_file, source_row, fld_zone, zone_subty, sfha, static_bfe, depth, study_typ, dfirm_id
            ORDER BY sfha DESC NULLS LAST, fld_zone, zone_subty
            """.formatted(POINT);

    private static final String TRACT_AT = """
            SELECT tract_fips, name FROM census_tracts
            WHERE geom IS NOT NULL AND ST_Intersects(geom, %s)
            ORDER BY tract_fips
            LIMIT 1
            """.formatted(POINT);

    private static final String NRI = """
            SELECT nri_version, risk_score, risk_rating,
                   hrcn_score, hrcn_rating, cfld_score, cfld_rating, ifld_score, ifld_rating, trnd_score, trnd_rating,
                   wfir_score, wfir_rating, swnd_score, swnd_rating, ltng_score, ltng_rating, hwav_score, hwav_rating
            FROM nri_tract_risk
            WHERE tract_fips = ?
            """;

    private static final String STORMS_LOADED = "SELECT EXISTS (SELECT 1 FROM storm_events WHERE state_fips = LEFT(?, 2))";

    private static final String STORMS_NEAR = """
            SELECT event_type, COUNT(*) AS n, (MAX(begin_at) AT TIME ZONE 'UTC')::date AS last_at,
                   SUM(damage_property_usd) AS damage
            FROM storm_events
            WHERE geom IS NOT NULL
              AND begin_at >= make_date(?, 1, 1)
              AND ST_DWithin(geom::geography, (%s)::geography, ?)
            GROUP BY event_type
            ORDER BY n DESC, event_type
            """.formatted(POINT);

    private static final String STORMS_IN_COUNTY = """
            SELECT COUNT(*) FROM storm_events WHERE fips5 = ? AND begin_at >= make_date(?, 1, 1)
            """;

    /** Uses idx_storm_events_state; zone events have fips5 NULL, so they are found by state. */
    private static final String ZONE_EVENT_TYPES = """
            SELECT event_type FROM storm_events
            WHERE state_fips = LEFT(?, 2) AND cz_type = 'Z' AND begin_at >= make_date(?, 1, 1)
            GROUP BY event_type
            ORDER BY COUNT(*) DESC, event_type
            """;

    private static final String[] HAZARD_CODES = {"HRCN", "CFLD", "IFLD", "TRND", "WFIR", "SWND", "LTNG", "HWAV"};

    private final JdbcTemplate jdbc;

    @Override
    public Optional<ParcelPoint> parcelPoint(long propertyId) {
        RowMapper<ParcelPoint> mapper = (rs, i) ->
                new ParcelPoint(rs.getString("fips_code"), rs.getDouble("lon"), rs.getDouble("lat"));
        return jdbc.query(PARCEL_POINT, mapper, propertyId).stream().findFirst();
    }

    @Override
    public Optional<LocalDate> loadedOn(String dataset, String fips5) {
        Date date = jdbc.queryForObject(LOADED_ON, Date.class, dataset, fips5);
        return Optional.ofNullable(date).map(Date::toLocalDate);
    }

    @Override
    public boolean floodZonesLoaded(String fips5) {
        return Boolean.TRUE.equals(jdbc.queryForObject(FLOOD_LOADED, Boolean.class, fips5));
    }

    @Override
    public List<FloodZoneRow> floodZonesAt(String fips5, double lon, double lat) {
        RowMapper<FloodZoneRow> mapper = (rs, i) -> new FloodZoneRow(
                rs.getString("fld_zone"), rs.getString("zone_subty"), rs.getObject("sfha", Boolean.class),
                decimal(rs, "static_bfe"), decimal(rs, "depth"), rs.getString("study_typ"), rs.getString("dfirm_id"));
        return jdbc.query(FLOOD_AT, mapper, fips5, lon, lat);
    }

    @Override
    public Optional<TractRow> tractAt(double lon, double lat) {
        RowMapper<TractRow> mapper = (rs, i) -> new TractRow(rs.getString("tract_fips"), rs.getString("name"));
        return jdbc.query(TRACT_AT, mapper, lon, lat).stream().findFirst();
    }

    @Override
    public Optional<NriRow> nri(String tractFips) {
        RowMapper<NriRow> mapper = (rs, i) -> {
            List<HazardRow> hazards = new java.util.ArrayList<>(HAZARD_CODES.length);
            for (String code : HAZARD_CODES) {
                String column = code.toLowerCase();
                hazards.add(new HazardRow(code, decimal(rs, column + "_score"), rs.getString(column + "_rating")));
            }
            return new NriRow(rs.getString("nri_version"), decimal(rs, "risk_score"), rs.getString("risk_rating"),
                    List.copyOf(hazards));
        };
        return jdbc.query(NRI, mapper, tractFips).stream().findFirst();
    }

    @Override
    public boolean stormEventsLoaded(String fips5) {
        return Boolean.TRUE.equals(jdbc.queryForObject(STORMS_LOADED, Boolean.class, fips5));
    }

    @Override
    public List<StormTypeRow> stormsNear(double lon, double lat, int radiusMeters, int fromYear) {
        RowMapper<StormTypeRow> mapper = (rs, i) -> {
            Date last = rs.getDate("last_at");
            BigDecimal damage = rs.getBigDecimal("damage");
            return new StormTypeRow(rs.getString("event_type"), rs.getInt("n"),
                    last == null ? null : last.toLocalDate(), damage == null ? null : damage.longValue());
        };
        return jdbc.query(STORMS_NEAR, mapper, fromYear, lon, lat, (double) radiusMeters);
    }

    @Override
    public int stormsInCounty(String fips5, int fromYear) {
        Integer count = jdbc.queryForObject(STORMS_IN_COUNTY, Integer.class, fips5, fromYear);
        return count == null ? 0 : count;
    }

    @Override
    public List<String> zoneEventTypes(String fips5, int fromYear) {
        return jdbc.queryForList(ZONE_EVENT_TYPES, String.class, fips5, fromYear);
    }

    /** NUMERIC column as a Double, null when the source holds no value. */
    private static Double decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }
}
