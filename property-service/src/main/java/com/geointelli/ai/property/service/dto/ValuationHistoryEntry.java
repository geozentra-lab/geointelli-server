package com.geointelli.ai.property.service.dto;

import java.time.Instant;

/** Version identity and archive time are separate from the original model/load dates in valuation.method. */
public record ValuationHistoryEntry(String runId, String inputHash, Instant archivedAt, ValuationResponse valuation,
        Long id, String provenance, String provenanceLabel, Instant scoredAt, Instant loadedAt,
        String rowSha256, ValuationModelEvidence modelEvidence, ValuationRunResponse run) {
}
