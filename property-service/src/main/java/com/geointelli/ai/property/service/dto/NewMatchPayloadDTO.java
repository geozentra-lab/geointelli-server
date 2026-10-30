package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/** One cadence-level summary; propertyIds is a bounded preview, while newMatchCount counts the complete scan. */
public record NewMatchPayloadDTO(String ruleName, String definitionHash, long matchedCount, long newMatchCount,
    List<Long> propertyIds, boolean previewTruncated,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant observationStartedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant observationFinishedAt) {
}
