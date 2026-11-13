package com.geointelli.ai.property.service.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.ValuationRefreshResponse;

/** Transactional source-change outbox and operator controls; Python owns processing. */
public interface ValuationRefreshService {
    /** Enqueues a verified ORM change on its existing transaction connection; never commits independently. */
    void sourceChanged(Connection connection, String trigger, Long propertyId, Long saleId) throws SQLException;

    /** Lists durable requests, optionally filtered by a valid processing status. */
    PageResponse<ValuationRefreshResponse> list(String status, int page, int size);

    /** Reads a request or returns 404, without exposing its worker fencing token. */
    ValuationRefreshResponse get(UUID id);

    /** Cancels only pending or failed work; a leased or completed request cannot be cancelled. */
    ValuationRefreshResponse cancel(UUID id);

    /** Retries failed Java-origin work with a new event identity; the original request remains immutable. */
    ValuationRefreshResponse retry(UUID id, String idempotencyKey);
}
