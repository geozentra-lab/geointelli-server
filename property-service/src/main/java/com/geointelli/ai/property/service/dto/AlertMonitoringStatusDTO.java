package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Rule-owner visibility into actual evaluation and delivery progress. */
public record AlertMonitoringStatusDTO(Long alertRuleId, boolean monitoringEnabled, boolean publisherEnabled,
    boolean effectivePaused, String status, boolean baselineEstablished, long generation, long scannedMatches,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastEvaluatedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant nextEvaluationAt, String lastErrorCode,
    long pendingEvents, long sentEvents, long failedEvents) {
}
