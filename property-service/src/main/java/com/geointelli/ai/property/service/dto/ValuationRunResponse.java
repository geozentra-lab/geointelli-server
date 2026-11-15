package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Publication provenance. Internal artifact paths and publisher identities are not exposed. */
public record ValuationRunResponse(String runId, String runKind, LocalDate asOf, Instant sourceSnapshotAt,
        String referenceRunId, Instant scoredAt, Instant publishedAt, String contentSha256,
        String manifestSha256, String inputsSha256) {
}
