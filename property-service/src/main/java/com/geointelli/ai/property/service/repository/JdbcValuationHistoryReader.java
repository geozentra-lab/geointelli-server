package com.geointelli.ai.property.service.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.ValuationHistorySnapshot;
import com.geointelli.ai.property.service.dto.ValuationRunResponse;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;

import lombok.RequiredArgsConstructor;

/** Read-only SQL adapter for Python's immutable publication tables. */
@Repository
@RequiredArgsConstructor
public class JdbcValuationHistoryReader implements ValuationHistoryReader {
    private final JdbcTemplate jdbc;
    private static final BeanPropertyRowMapper<PropertyValuePrediction> PREDICTION =
        new BeanPropertyRowMapper<>(PropertyValuePrediction.class) {
            @Override
            protected String lowerCaseName(String name) {
                return super.lowerCaseName(name).replace("_", "");
            }
        };

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ValuationHistorySnapshot> findByPropertyId(Long propertyId, Pageable pageable) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM property_value_prediction_history WHERE property_id = ?",
            Long.class, propertyId);
        var rows = jdbc.query("""
            SELECT h.*, r.run_id AS registered_run_id, r.run_kind, r.as_of, r.source_snapshot_at,
                   r.reference_run_id, r.scored_at AS run_scored_at, r.published_at,
                   r.content_sha256, r.manifest_sha256, r.inputs_sha256
            FROM property_value_prediction_history h
            LEFT JOIN property_valuation_runs r ON r.run_id = h.run_id
            WHERE h.property_id = ? ORDER BY h.loaded_at DESC, h.id DESC LIMIT ? OFFSET ?
            """, (rs, row) -> snapshot(rs, row), propertyId, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    private ValuationHistorySnapshot snapshot(ResultSet rs, int row) throws SQLException {
        var prediction = PREDICTION.mapRow(rs, row);
        // A history storage ID is not a current-prediction ID. loaded_at is an Instant, not a naive created_at.
        prediction.setId(null);
        prediction.setCreatedAt(null);
        prediction.setUpdatedAt(null);
        ValuationRunResponse run = rs.getString("registered_run_id") == null ? null : new ValuationRunResponse(
            rs.getString("registered_run_id"), rs.getString("run_kind"), rs.getObject("as_of", LocalDate.class),
            instant(rs, "source_snapshot_at"), rs.getString("reference_run_id"), instant(rs, "run_scored_at"),
            instant(rs, "published_at"), rs.getString("content_sha256"), rs.getString("manifest_sha256"),
            rs.getString("inputs_sha256"));
        return new ValuationHistorySnapshot(prediction, instant(rs, "archived_at"), null, rs.getLong("id"),
            rs.getString("provenance"), instant(rs, "scored_at"), instant(rs, "loaded_at"),
            rs.getString("row_sha256"), rs.getString("evidence"), run);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
