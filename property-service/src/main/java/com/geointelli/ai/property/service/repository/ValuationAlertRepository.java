package com.geointelli.ai.property.service.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

/**
 * Frozen publication reads and progress updates in the evaluator's parent-rule
 * transaction.
 */
@Repository
@RequiredArgsConstructor
public class ValuationAlertRepository {
    private final JdbcTemplate jdbc;

    /**
     * A history identity is stable even though Python replaces current prediction
     * rows.
     */
    public record Version(long id, String provenance, String runId, String modelVersion, LocalDate predictionDate,
            String status, BigDecimal value, Instant loadedAt) {
    }

    /**
     * Current publication and the cursor boundary observed in the same snapshot.
     */
    public record Latest(Version version, long cursorBoundary, boolean currentUsable) {
    }

    /**
     * An unavailable observation retains progress but deliberately has no
     * comparison value.
     */
    public record Observation(long cursorHistoryId, Long comparisonHistoryId) {
    }

    /**
     * Reads the latest recorded version; missing/unsupported current estimates are
     * not substituted by history.
     */
    public Latest latest(long propertyId) {
        List<Latest> rows = jdbc.query("""
                SELECT h.*, max(h.id) OVER () AS cursor_boundary,
                    EXISTS (SELECT 1 FROM property_value_predictions p WHERE p.property_id=h.property_id
                        AND p.run_id IS NOT DISTINCT FROM h.run_id
                        AND p.valuation_status IN ('supported','limited') AND p.predicted_price>0
                        AND p.predicted_price=h.predicted_price) AS current_usable
                FROM property_value_prediction_history h WHERE h.property_id=?
                ORDER BY h.loaded_at DESC,h.id DESC LIMIT 1
                """, (row, index) -> new Latest(version(row), row.getLong("cursor_boundary"),
                row.getBoolean("current_usable")), propertyId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Reads a rule's last committed observation; evaluation already holds the
     * parent rule lock.
     */
    public Observation observation(long ruleId, long propertyId) {
        List<Observation> rows = jdbc.query("""
                SELECT cursor_history_id,comparison_history_id FROM alert_valuation_observations
                WHERE rule_id=? AND property_id=?
                """, (row, index) -> new Observation(row.getLong(1), row.getObject(2, Long.class)), ruleId, propertyId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Reads each new committed publication in insertion order, bounded per
     * transaction.
     */
    public List<Version> after(long propertyId, long cursor, int limit) {
        return jdbc.query(
                "SELECT * FROM property_value_prediction_history WHERE property_id=? AND id>? ORDER BY id LIMIT ?",
                (row, index) -> version(row), propertyId, cursor, limit);
    }

    /**
     * Resolves the frozen previous estimate, always scoped to the same property.
     */
    public Version version(long propertyId, long id) {
        List<Version> rows = jdbc.query("SELECT * FROM property_value_prediction_history WHERE property_id=? AND id=?",
                (row, index) -> version(row), propertyId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Commits cursor and comparison identity together with any new outbox events.
     */
    public void observe(long ruleId, long propertyId, long cursor, Long comparison, long generation) {
        jdbc.update(
                """
                        INSERT INTO alert_valuation_observations(rule_id,property_id,cursor_history_id,comparison_history_id,seen_generation)
                        VALUES(?,?,?,?,?) ON CONFLICT(rule_id,property_id) DO UPDATE SET
                            cursor_history_id=EXCLUDED.cursor_history_id, comparison_history_id=EXCLUDED.comparison_history_id,
                            seen_generation=EXCLUDED.seen_generation
                        """,
                ruleId, propertyId, cursor, comparison, generation);
    }

    /**
     * A changed definition establishes a new silent baseline rather than replaying
     * earlier changes.
     */
    public void clear(long ruleId) {
        jdbc.update("DELETE FROM alert_valuation_observations WHERE rule_id=?", ruleId);
    }

    /**
     * Properties leaving a saved search lose their baseline and are silently
     * baselined if they re-enter.
     */
    public void retainGeneration(long ruleId, long generation) {
        jdbc.update("DELETE FROM alert_valuation_observations WHERE rule_id=? AND seen_generation<>?", ruleId,
                generation);
    }

    private Version version(ResultSet row) throws SQLException {
        return new Version(row.getLong("id"), row.getString("provenance"), row.getString("run_id"),
                row.getString("model_version"), row.getObject("prediction_date", LocalDate.class),
                row.getString("valuation_status"),
                row.getBigDecimal("predicted_price"), row.getTimestamp("loaded_at").toInstant());
    }
}
