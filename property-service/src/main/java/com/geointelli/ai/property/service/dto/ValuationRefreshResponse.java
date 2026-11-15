package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.util.UUID;

/** Safe processing diagnostics; worker tokens and internal errors are not exposed. */
public record ValuationRefreshResponse(UUID requestId, String eventKey, String triggerType, String scopeType,
        Long propertyId, Long saleId, String sourceVersion, Instant sourceChangedAt, String status,
        String outcome, int attempts, int maxAttempts, Instant nextAttemptAt, String runId,
        String lastErrorCode, Instant leaseExpiresAt, Instant createdAt, Instant updatedAt,
        Instant startedAt, Instant completedAt) {
}
