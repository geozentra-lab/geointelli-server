package com.geointelli.ai.property.service.repository;

import com.geointelli.ai.property.service.dto.EconomicIndicatorDTO;
import com.geointelli.ai.property.service.dto.EconomicIndicatorImportResultDTO;
import com.geointelli.ai.property.service.dto.EconomicIndicatorObservationDTO;
import com.geointelli.ai.property.service.dto.EconomicObservation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes the economic indicators of migration V50 (SCRUM-133) with plain SQL. One row per indicator,
 * county and period: an import of a period already stored updates that row (a revision) and never adds a second one.
 */
@Repository
public class EconomicIndicatorJdbcRepository {

    /** Rows per JDBC batch when observations are written. */
    static final int BATCH_SIZE = 1000;

    private static final String INDICATORS = """
            SELECT i.code, i.name, i.description, i.unit, i.frequency, i.geography_level, i.source, i.source_name,
                   i.source_url,
                   count(DISTINCT o.geography_code) AS counties, count(o.id) AS observations,
                   count(o.id) FILTER (WHERE o.observation_kind = 'ESTIMATE') AS estimate_observations,
                   min(o.period_start) AS first_period, max(o.period_end) AS last_period,
                   max(o.last_verified_at) AS last_verified_at, max(o.revised_at) AS last_revised_at
            FROM economic_indicators i
            LEFT JOIN economic_indicator_observations o ON o.indicator_code = i.code
            """;

    private static final String FIND_ALL = INDICATORS + " GROUP BY i.code ORDER BY i.source, i.code";

    private static final String FIND_ONE = INDICATORS + " WHERE i.code = :code GROUP BY i.code";

    private static final String COUNTIES = """
            SELECT fips_code, name FROM counties
            WHERE fips_code LIKE :stateFips || '%'
            ORDER BY fips_code
            """;

    /**
     * Insert, or update the row of the same indicator, county and period. The CASE expressions read the row before
     * the update: a changed value is kept in previous_value; a changed value or kind is counted as a revision. A
     * row from a newer vintage is left as it is (WHERE), so an older file never undoes a revision.
     */
    private static final String UPSERT = """
            INSERT INTO economic_indicator_observations AS o (indicator_code, geography_level, geography_code,
                geography_name, period_start, period_end, value, observation_kind, source_series_id, source_vintage,
                source_url, ingested_at, last_verified_at)
            VALUES (:indicatorCode, 'COUNTY', :geographyCode, :geographyName, :periodStart, :periodEnd, :value,
                :observationKind, :seriesId, :vintage, :sourceUrl, :now, :now)
            ON CONFLICT (indicator_code, geography_level, geography_code, period_start) DO UPDATE SET
                previous_value = CASE WHEN o.value <> EXCLUDED.value THEN o.value ELSE o.previous_value END,
                revision_count = o.revision_count + CASE
                    WHEN o.value <> EXCLUDED.value OR o.observation_kind <> EXCLUDED.observation_kind THEN 1 ELSE 0 END,
                revised_at = CASE WHEN o.value <> EXCLUDED.value OR o.observation_kind <> EXCLUDED.observation_kind
                    THEN EXCLUDED.last_verified_at ELSE o.revised_at END,
                value = EXCLUDED.value,
                observation_kind = EXCLUDED.observation_kind,
                geography_name = EXCLUDED.geography_name,
                period_end = EXCLUDED.period_end,
                source_series_id = EXCLUDED.source_series_id,
                source_vintage = EXCLUDED.source_vintage,
                source_url = EXCLUDED.source_url,
                last_verified_at = EXCLUDED.last_verified_at
            WHERE o.source_vintage COLLATE "C" <= EXCLUDED.source_vintage COLLATE "C"
            """;

    /** Rows an import touched, found by its timestamp: inserted, revised, and every row it wrote. */
    private static final String RUN_COUNTS = """
            SELECT count(*) FILTER (WHERE ingested_at = :now) AS inserted,
                   count(*) FILTER (WHERE revised_at = :now) AS revised,
                   count(*) AS written
            FROM economic_indicator_observations
            WHERE indicator_code IN (:codes) AND last_verified_at = :now
            """;

    private static final String RECORD_LOAD = """
            INSERT INTO economic_indicator_loads (source, source_url, source_vintage, state_fips, period_from,
                period_to, received, inserted, revised, unchanged, older_vintage, missing_values, loaded_by, loaded_at)
            VALUES (:source, :sourceUrl, :vintage, :stateFips, :periodFrom, :periodTo, :received, :inserted, :revised,
                :unchanged, :olderVintage, :missingValues, :loadedBy, :loadedAt)
            """;

    private static final String OBSERVATIONS = """
            SELECT geography_code, geography_name, period_start, period_end, value, observation_kind,
                   source_series_id, source_vintage, source_url, previous_value, revision_count, ingested_at,
                   revised_at, last_verified_at
            FROM economic_indicator_observations
            WHERE indicator_code = :code AND geography_level = 'COUNTY' AND geography_code = :geographyCode
              AND (CAST(:from AS DATE) IS NULL OR period_end >= CAST(:from AS DATE))
              AND (CAST(:to AS DATE) IS NULL OR period_end <= CAST(:to AS DATE))
            ORDER BY period_start
            """;

    private static final RowMapper<EconomicIndicatorDTO> INDICATOR = (rs, row) -> new EconomicIndicatorDTO(
            rs.getString("code"), rs.getString("name"), rs.getString("description"), rs.getString("unit"),
            rs.getString("frequency"), rs.getString("geography_level"), rs.getString("source"),
            rs.getString("source_name"), rs.getString("source_url"),
            new EconomicIndicatorDTO.Coverage(rs.getInt("counties"), rs.getInt("observations"),
                    rs.getInt("estimate_observations"), rs.getObject("first_period", LocalDate.class),
                    rs.getObject("last_period", LocalDate.class), instant(rs, "last_verified_at"),
                    instant(rs, "last_revised_at")));

    private final NamedParameterJdbcTemplate jdbc;

    public EconomicIndicatorJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every indicator with what is stored for it, BLS first. */
    public List<EconomicIndicatorDTO> findIndicators() {
        return jdbc.query(FIND_ALL, INDICATOR);
    }

    /** One indicator with what is stored for it; empty when the code is unknown. */
    public Optional<EconomicIndicatorDTO> findIndicator(String code) {
        return jdbc.query(FIND_ONE, new MapSqlParameterSource("code", code), INDICATOR).stream().findFirst();
    }

    /** The counties of a state that have a FIPS code (migration V42), by FIPS code: 12086 to Miami-Dade. */
    public Map<String, String> findCounties(String stateFips) {
        Map<String, String> counties = new LinkedHashMap<>();
        jdbc.query(COUNTIES, new MapSqlParameterSource("stateFips", stateFips),
                rs -> { counties.put(rs.getString("fips_code"), rs.getString("name")); });
        return counties;
    }

    /**
     * Inserts new periods and updates stored ones, in batches of {@link #BATCH_SIZE}.
     *
     * @param vintage   BLS: date the API was read; Census: vintage year
     * @param sourceUrl where the values were read
     * @param now       time of the import, written to ingested_at, revised_at and last_verified_at
     */
    public void upsert(List<EconomicObservation> observations, String vintage, String sourceUrl, OffsetDateTime now) {
        for (int from = 0; from < observations.size(); from += BATCH_SIZE) {
            List<EconomicObservation> batch = observations.subList(from,
                    Math.min(from + BATCH_SIZE, observations.size()));
            SqlParameterSource[] parameters = batch.stream()
                    .map(observation -> parameters(observation, vintage, sourceUrl, now))
                    .toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate(UPSERT, parameters);
        }
    }

    /**
     * What an import wrote, found by its timestamp.
     *
     * @param codes indicators of the source
     * @param now   time of the import, as given to {@link #upsert}
     */
    public RunCounts countRun(Collection<String> codes, OffsetDateTime now) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("codes", codes)
                .addValue("now", now, Types.TIMESTAMP_WITH_TIMEZONE);
        return jdbc.queryForObject(RUN_COUNTS, parameters, (rs, row) ->
                new RunCounts(rs.getInt("inserted"), rs.getInt("revised"), rs.getInt("written")));
    }

    /** Records a successful import in economic_indicator_loads. */
    public void recordLoad(EconomicIndicatorImportResultDTO result, String loadedBy) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("source", result.source())
                .addValue("sourceUrl", result.sourceUrl())
                .addValue("vintage", result.sourceVintage())
                .addValue("stateFips", result.stateFips())
                .addValue("periodFrom", result.periodFrom(), Types.DATE)
                .addValue("periodTo", result.periodTo(), Types.DATE)
                .addValue("received", result.received())
                .addValue("inserted", result.inserted())
                .addValue("revised", result.revised())
                .addValue("unchanged", result.unchanged())
                .addValue("olderVintage", result.olderVintage())
                .addValue("missingValues", result.missingValues())
                .addValue("loadedBy", loadedBy, Types.VARCHAR)
                .addValue("loadedAt", result.importedAt().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
        jdbc.update(RECORD_LOAD, parameters);
    }

    /**
     * Stored values of one indicator for one county, oldest first.
     *
     * @param monthly true for a monthly indicator: the period is written 2026-03, otherwise 2024
     * @param from    only periods ending on or after this date; null for no limit
     * @param to      only periods ending on or before this date; null for no limit
     */
    public List<EconomicIndicatorObservationDTO> findObservations(String code, String geographyCode, boolean monthly,
                                                                  LocalDate from, LocalDate to) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("geographyCode", geographyCode)
                .addValue("from", from, Types.DATE)
                .addValue("to", to, Types.DATE);
        return jdbc.query(OBSERVATIONS, parameters, (rs, row) -> {
            LocalDate periodStart = rs.getObject("period_start", LocalDate.class);
            LocalDate periodEnd = rs.getObject("period_end", LocalDate.class);
            String period = monthly ? YearMonth.from(periodStart).toString() : String.valueOf(periodEnd.getYear());
            return new EconomicIndicatorObservationDTO(rs.getString("geography_code"),
                    rs.getString("geography_name"), period, periodStart, periodEnd, rs.getBigDecimal("value"),
                    rs.getString("observation_kind"), rs.getString("source_series_id"),
                    rs.getString("source_vintage"), rs.getString("source_url"),
                    rs.getBigDecimal("previous_value"), rs.getInt("revision_count"), instant(rs, "ingested_at"),
                    instant(rs, "revised_at"), instant(rs, "last_verified_at"));
        });
    }

    private static SqlParameterSource parameters(EconomicObservation observation, String vintage, String sourceUrl,
                                                 OffsetDateTime now) {
        return new MapSqlParameterSource()
                .addValue("indicatorCode", observation.indicatorCode())
                .addValue("geographyCode", observation.geographyCode())
                .addValue("geographyName", observation.geographyName(), Types.VARCHAR)
                .addValue("periodStart", observation.periodStart(), Types.DATE)
                .addValue("periodEnd", observation.periodEnd(), Types.DATE)
                .addValue("value", observation.value(), Types.NUMERIC)
                .addValue("observationKind", observation.observationKind())
                .addValue("seriesId", observation.sourceSeriesId(), Types.VARCHAR)
                .addValue("vintage", vintage)
                .addValue("sourceUrl", sourceUrl)
                .addValue("now", now, Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /**
     * Rows an import wrote.
     *
     * @param inserted new periods
     * @param revised  stored periods whose value or kind changed
     * @param written  every row written (inserted, revised or confirmed unchanged)
     */
    public record RunCounts(int inserted, int revised, int written) {
    }
}
