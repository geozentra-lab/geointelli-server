package com.geointelli.ai.property.service.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.OpportunityScoreDTO;
import com.geointelli.ai.property.service.dto.OpportunitySignals;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Reads the evidence of the opportunity score (property location and stored valuation) and writes the stored
 * scores (SCRUM-76). The same signal query serves the live score of one property and the batch refresh, so both
 * see identical evidence.
 */
@Repository
public class OpportunityScoreJdbcRepository {

    private static final String SIGNAL_COLUMNS = """
            pr.id AS property_id, pr.county_id, c.state_id, v.property_id AS valuation_property_id,
            v.valuation_status, v.predicted_price, v.forecast_1y, v.forecast_3y, v.market_outlook_1y_pct,
            v.confidence, v.confidence_score, v.model_version, v.data_through, v.run_id
            """;

    private static final String FIND_SIGNALS = "SELECT " + SIGNAL_COLUMNS + """
            FROM properties pr
            LEFT JOIN counties c ON c.id = pr.county_id
            LEFT JOIN property_value_predictions v ON v.property_id = pr.id
            WHERE pr.id = :propertyId
            """;

    /**
     * Properties with a valuation after a key, in key order; in incremental mode only those whose score is missing or
     * stale. The key bound is repeated on {@code pr.id}: PostgreSQL does not carry a range condition across the join,
     * and without it every batch scanned the properties index from the first ID (about 1 s per batch at 300,000 on
     * the local data instead of 4 ms).
     */
    private static final String FIND_CANDIDATES = "SELECT " + SIGNAL_COLUMNS + """
            FROM property_value_predictions v
            JOIN properties pr ON pr.id = v.property_id
            LEFT JOIN counties c ON c.id = pr.county_id
            LEFT JOIN property_opportunity_scores s ON s.property_id = v.property_id
            WHERE v.property_id > :after AND pr.id > :after
              AND (CAST(:all AS BOOLEAN) OR s.property_id IS NULL OR s.score_version <> :scoreVersion
                   OR s.valuation_run_id IS DISTINCT FROM v.run_id)
            ORDER BY v.property_id
            LIMIT :limit
            """;

    private static final String UPSERT = """
            INSERT INTO property_opportunity_scores (property_id, score_version, status, score, coverage_percent,
                factors, reasons, county_id, state_id, estimated_value, forecast_growth_1y_pct, valuation_run_id,
                valuation_model_version, valuation_data_through, scored_at)
            VALUES (:propertyId, :scoreVersion, :status, :score, :coverage, CAST(:factors AS JSONB),
                CAST(:reasons AS JSONB), :countyId, :stateId, :estimatedValue, :growth1y, :runId, :modelVersion,
                :dataThrough, :scoredAt)
            ON CONFLICT (property_id) DO UPDATE SET
                score_version = EXCLUDED.score_version, status = EXCLUDED.status, score = EXCLUDED.score,
                coverage_percent = EXCLUDED.coverage_percent, factors = EXCLUDED.factors, reasons = EXCLUDED.reasons,
                county_id = EXCLUDED.county_id, state_id = EXCLUDED.state_id, estimated_value = EXCLUDED.estimated_value,
                forecast_growth_1y_pct = EXCLUDED.forecast_growth_1y_pct, valuation_run_id = EXCLUDED.valuation_run_id,
                valuation_model_version = EXCLUDED.valuation_model_version,
                valuation_data_through = EXCLUDED.valuation_data_through, scored_at = EXCLUDED.scored_at
            """;

    private static final String DELETE_WITHOUT_VALUATION = """
            DELETE FROM property_opportunity_scores s
            WHERE NOT EXISTS (SELECT 1 FROM property_value_predictions v WHERE v.property_id = s.property_id)
            """;

    private static final RowMapper<OpportunitySignals> SIGNALS = (rs, row) -> {
        Long propertyId = rs.getLong("property_id");
        Long countyId = rs.getObject("county_id", Long.class);
        Long stateId = rs.getObject("state_id", Long.class);
        if (rs.getObject("valuation_property_id") == null) {
            return OpportunitySignals.withoutValuation(propertyId, countyId, stateId);
        }
        return new OpportunitySignals(propertyId, countyId, stateId, true, rs.getString("valuation_status"),
                rs.getBigDecimal("predicted_price"), rs.getBigDecimal("forecast_1y"), rs.getBigDecimal("forecast_3y"),
                rs.getObject("market_outlook_1y_pct", Double.class), rs.getString("confidence"),
                rs.getObject("confidence_score", Integer.class), rs.getString("model_version"),
                rs.getObject("data_through", LocalDate.class), rs.getString("run_id"));
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public OpportunityScoreJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * The evidence of one property.
     *
     * @return empty when the property does not exist; signals without a valuation when it has none
     */
    public Optional<OpportunitySignals> findSignals(long propertyId) {
        return jdbc.query(FIND_SIGNALS, new MapSqlParameterSource("propertyId", propertyId), SIGNALS)
                .stream().findFirst();
    }

    /**
     * The next properties with a valuation, ordered by property ID.
     *
     * @param afterPropertyId only properties with a greater ID (0 to start)
     * @param limit           maximum number of properties
     * @param all             every property with a valuation; otherwise only those without a stored score for
     *                        {@code scoreVersion} and the current valuation run
     * @param scoreVersion    current scoring version
     */
    public List<OpportunitySignals> findCandidates(long afterPropertyId, int limit, boolean all, String scoreVersion) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("after", afterPropertyId)
                .addValue("limit", limit)
                .addValue("all", all)
                .addValue("scoreVersion", scoreVersion);
        return jdbc.query(FIND_CANDIDATES, params, SIGNALS);
    }

    /** Inserts or replaces the stored score of each property. */
    public void upsert(List<ScoredProperty> scores) {
        if (scores.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = scores.stream().map(this::parameters).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT, batch);
    }

    /** Removes the stored scores of properties that no longer have a valuation; returns how many were removed. */
    public int deleteWithoutValuation() {
        return jdbc.update(DELETE_WITHOUT_VALUATION, new MapSqlParameterSource());
    }

    private SqlParameterSource parameters(ScoredProperty scored) {
        OpportunitySignals signals = scored.signals();
        OpportunityScoreDTO score = scored.score();
        OpportunityScoreDTO.ValuationBasis valuation = score.valuation();
        return new MapSqlParameterSource()
                .addValue("propertyId", score.propertyId())
                .addValue("scoreVersion", score.scoreVersion())
                .addValue("status", score.status())
                .addValue("score", score.score(), Types.NUMERIC)
                .addValue("coverage", score.coveragePercent())
                .addValue("factors", toJson(score.factors()))
                .addValue("reasons", toJson(score.reasons()))
                .addValue("countyId", signals.countyId(), Types.BIGINT)
                .addValue("stateId", signals.stateId(), Types.BIGINT)
                .addValue("estimatedValue", valuation == null ? null : valuation.estimatedValue(), Types.NUMERIC)
                .addValue("growth1y", growth1y(score), Types.NUMERIC)
                .addValue("runId", valuation == null ? null : valuation.runId(), Types.VARCHAR)
                .addValue("modelVersion", valuation == null ? null : valuation.modelVersion(), Types.VARCHAR)
                .addValue("dataThrough", valuation == null ? null : valuation.dataThrough(), Types.DATE)
                .addValue("scoredAt", score.scoredAt().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    /** One-year forecast growth in percent, as shown by its factor; null when unavailable. */
    private static BigDecimal growth1y(OpportunityScoreDTO score) {
        // The factor is selected before its signal is read: an unavailable factor has a null signal, and a
        // stream element of null would make findFirst() throw.
        return score.factors().stream()
                .filter(factor -> "FORECAST_GROWTH_1Y".equals(factor.code()))
                .findFirst()
                .map(OpportunityScoreDTO.Factor::signal)
                .orElse(null);
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize opportunity score", error);
        }
    }

    /** A property's evidence and the score calculated from it. */
    public record ScoredProperty(OpportunitySignals signals, OpportunityScoreDTO score) {
    }
}
