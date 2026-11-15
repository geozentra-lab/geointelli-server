package com.geointelli.ai.property.service.service.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.ValuationRefreshResponse;
import com.geointelli.ai.property.service.service.ValuationRefreshService;

/** Inserts requests atomically with ORM source changes; never modifies Python's leases or outcomes. */
@Service
public class ValuationRefreshServiceImpl implements ValuationRefreshService {
    private static final List<String> STATUSES = List.of("PENDING", "LEASED", "COMPLETED", "FAILED", "CANCELLED");
    private final JdbcTemplate jdbc;
    private final boolean fullMarket;

    /** Configures the outbox; FULL_MARKET scope is an explicit bulk-import deployment choice. */
    public ValuationRefreshServiceImpl(JdbcTemplate jdbc,
            @Value("${valuation.refresh.scope:PROPERTY}") String scope) {
        if (!List.of("PROPERTY", "FULL_MARKET").contains(scope))
            throw new IllegalArgumentException("valuation.refresh.scope must be PROPERTY or FULL_MARKET");
        this.jdbc = jdbc;
        this.fullMarket = "FULL_MARKET".equals(scope);
    }

    /** {@inheritDoc} */
    @Override
    public void sourceChanged(Connection connection, String trigger, Long propertyId, Long saleId) throws SQLException {
        if (connection.getAutoCommit()) throw new IllegalStateException("Refresh enqueue requires a source transaction");
        if (!List.of("SALE_CHANGE", "PROPERTY_CHANGE").contains(trigger) || propertyId == null)
            throw new IllegalArgumentException("A verified sale/property change and property ID are required");
        String version;
        try (var statement = connection.prepareStatement("SELECT txid_current()::text"); var result = statement.executeQuery()) {
            result.next();
            version = "tx:" + result.getString(1);
        }
        // Collapse repeated writes in one transaction only. A later transaction always creates new work,
        // even when inputs change back to an earlier value or an earlier request is currently leased.
        String key = fullMarket ? "PROPERTY:BULK:" + version
            : ("SALE_CHANGE".equals(trigger) ? "SALE:" + saleId + ":" + propertyId : "PROPERTY:" + propertyId) + ":" + version;
        try (var statement = connection.prepareStatement("""
            INSERT INTO property_valuation_refresh_requests
                (request_id, event_key, trigger_type, scope_type, property_id, sale_id, source_version)
            VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (event_key) DO NOTHING
            """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, key);
            statement.setString(3, fullMarket ? "PROPERTY_CHANGE" : trigger);
            statement.setString(4, fullMarket ? "FULL_MARKET" : "PROPERTY");
            statement.setObject(5, fullMarket ? null : propertyId);
            statement.setObject(6, fullMarket ? null : saleId);
            statement.setString(7, version);
            statement.executeUpdate();
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ValuationRefreshResponse> list(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (status != null && !STATUSES.contains(status)))
            throw new IllegalArgumentException("Use a valid status, page >= 0 and size between 1 and 100");
        String where = status == null ? "" : " WHERE status = ?";
        Object[] countArgs = status == null ? new Object[] {} : new Object[] {status};
        Long total = jdbc.queryForObject("SELECT count(*) FROM property_valuation_refresh_requests" + where,
            Long.class, countArgs);
        Object[] args = status == null ? new Object[] {size, (long) page * size}
            : new Object[] {status, size, (long) page * size};
        var rows = jdbc.query("SELECT * FROM property_valuation_refresh_requests" + where
            + " ORDER BY created_at DESC, request_id DESC LIMIT ? OFFSET ?", this::row, args);
        return PageResponse.from(new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ValuationRefreshResponse get(UUID id) {
        return read(id, false);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ValuationRefreshResponse cancel(UUID id) {
        var request = read(id, true);
        if ("CANCELLED".equals(request.status())) return request;
        if (!List.of("PENDING", "FAILED").contains(request.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending or failed requests can be cancelled");
        jdbc.update("UPDATE property_valuation_refresh_requests SET status = 'CANCELLED', updated_at = now() WHERE request_id = ?", id);
        return read(id, false);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ValuationRefreshResponse retry(UUID id, String idempotencyKey) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Idempotency-Key must contain 1-80 letters, digits, underscores or hyphens");
        var old = read(id, true);
        if (!"FAILED".equals(old.status()) || "MODEL_PUBLICATION".equals(old.triggerType()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed Java-origin requests can be retried here");
        String key = "RETRY:" + id + ":" + idempotencyKey;
        jdbc.update("""
            INSERT INTO property_valuation_refresh_requests
                (request_id,event_key,trigger_type,scope_type,property_id,sale_id,source_version,max_attempts)
            VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (event_key) DO NOTHING
            """, UUID.randomUUID(), key, old.triggerType(), old.scopeType(), old.propertyId(), old.saleId(),
            old.sourceVersion(), old.maxAttempts());
        return jdbc.queryForObject("SELECT * FROM property_valuation_refresh_requests WHERE event_key = ?", this::row, key);
    }

    private ValuationRefreshResponse read(UUID id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM property_valuation_refresh_requests WHERE request_id = ?"
            + (lock ? " FOR UPDATE" : ""), this::row, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Valuation refresh request not found");
        return rows.get(0);
    }

    private ValuationRefreshResponse row(ResultSet rs, int index) throws SQLException {
        return new ValuationRefreshResponse(rs.getObject("request_id", UUID.class), rs.getString("event_key"),
            rs.getString("trigger_type"), rs.getString("scope_type"), rs.getObject("property_id", Long.class),
            rs.getObject("sale_id", Long.class), rs.getString("source_version"), instant(rs,"source_changed_at"),
            rs.getString("status"), rs.getString("outcome"), rs.getInt("attempts"), rs.getInt("max_attempts"),
            instant(rs,"next_attempt_at"), rs.getString("run_id"), rs.getString("last_error_code"),
            instant(rs,"lease_expires_at"), instant(rs,"created_at"), instant(rs,"updated_at"),
            instant(rs,"started_at"), instant(rs,"completed_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
