package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.geointelli.ai.property.service.entity.ValuationChangeDirection;

/**
 * Frozen USD estimates and an explicitly defined percentage: (new - old) / old
 * * 100.
 */
public record ValuationChangePayloadDTO(String ruleName, String currency, Snapshot oldValuation,
        Snapshot newValuation, BigDecimal changeAmount, BigDecimal changePercent,
        BigDecimal minimumChangePercent, ValuationChangeDirection configuredDirection,
        ValuationChangeDirection direction,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant observationStartedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant observationFinishedAt) {
    /**
     * Stored publication data, not current prediction IDs or recalculated values.
     */
    public record Snapshot(long historyId, String runId, String modelVersion,
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate predictionDate,
            String status, BigDecimal value,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant loadedAt) {
    }
}
