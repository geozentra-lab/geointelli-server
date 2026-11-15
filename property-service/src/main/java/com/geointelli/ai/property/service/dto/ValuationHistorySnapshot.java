package com.geointelli.ai.property.service.dto;

import java.time.Instant;

import com.geointelli.ai.property.service.entity.PropertyValuePrediction;

/**
 * Internal frozen publication, not a writable database entity. Instant load/scoring timestamps remain
 * separate from the current table's naive created_at. Missing evidence is never filled from current sales.
 */
public record ValuationHistorySnapshot(PropertyValuePrediction prediction, Instant archivedAt,
        ComparableResponse supportingComparables, Long id, String provenance, Instant scoredAt,
        Instant loadedAt, String rowSha256, String evidence, ValuationRunResponse run) {
    /** Creates an in-memory snapshot without additional publication metadata. */
    public ValuationHistorySnapshot(PropertyValuePrediction prediction, Instant archivedAt,
            ComparableResponse supportingComparables) {
        this(prediction, archivedAt, supportingComparables, null, null, null, null, null, null, null);
    }
}
