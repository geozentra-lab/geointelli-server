package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Outcome of one opportunity score refresh run (SCRUM-76). */
public record OpportunityRefreshResultDTO(
        @Schema(description = "INCREMENTAL (missing or stale scores only) or FULL (every valued property)",
                example = "INCREMENTAL")
        String mode,
        @Schema(description = "Scoring version written", example = "opportunity-v1")
        String scoreVersion,
        @Schema(description = "Properties scored and stored in this run", example = "420061")
        long processed,
        @Schema(description = "Of those, scores with status SCORED", example = "419985")
        long scored,
        @Schema(description = "Of those, scores with status UNAVAILABLE", example = "76")
        long unavailable,
        @Schema(description = "Stored scores removed because the property no longer has a valuation", example = "0")
        int removed,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant finishedAt,
        @Schema(description = "Run time in milliseconds", example = "95000")
        long durationMillis
) {
}
