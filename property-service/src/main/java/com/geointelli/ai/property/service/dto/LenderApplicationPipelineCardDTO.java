package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Application pipeline card of the lender dashboard (SCRUM-87). Counts the organization's recorded applications by
 * their current stage. It is a snapshot taken at generatedAt, not a historical period: every recorded application is
 * counted once, whatever its creation date.
 */
public record LenderApplicationPipelineCardDTO(
        @Schema(description = "Organization the applications belong to", example = "42")
        Long organizationId,
        @Schema(description = "OK with at least one application, EMPTY otherwise, ERROR when the composed dashboard "
                + "could not load this card", example = STATUS_OK)
        String status,
        @Schema(description = "Why the card failed; null unless status is ERROR")
        String error,
        @Schema(description = "When the card was built (UTC)")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant generatedAt,
        @Schema(description = "Period the counts cover")
        ReportingPeriod reportingPeriod,
        @Schema(description = "Number of applications, the sum of every stage", example = "9")
        long totalApplications,
        @Schema(description = "Every supported stage in pipeline order, 0 included")
        List<StageCount> stages,
        @Schema(description = "How the figures are built")
        String note
) {
    public static final String STATUS_OK = "OK";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_ERROR = "ERROR";
    public static final String PERIOD_TYPE = "CURRENT_STATUS";
    public static final String PERIOD_DESCRIPTION = "Snapshot of every recorded application by its current stage "
            + "at asOf; not filtered by date";

    /** The counts are a snapshot of the current stages at asOf. */
    public record ReportingPeriod(
            @Schema(description = "Kind of period", example = PERIOD_TYPE) String type,
            @Schema(description = "Moment of the snapshot (UTC)")
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant asOf,
            @Schema(description = "What the period covers", example = PERIOD_DESCRIPTION) String description) {}

    /** Number of applications currently in one stage. */
    public record StageCount(
            @Schema(description = "Pipeline stage", example = "SUBMITTED") LenderApplicationStage stage,
            @Schema(description = "Number of applications currently in this stage", example = "3") long count) {}
}
