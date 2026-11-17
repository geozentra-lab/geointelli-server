package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Admin coverage and provider-health dashboard (SCRUM-92). Every figure is counted from recorded rows: data-quality
 * findings (SCRUM-189), source rows per property and recorded ingestion/backfill jobs. A block that cannot be
 * measured says so with status UNAVAILABLE and a reason; it never shows a guessed or default value.
 */
public record AdminCoverageHealthDTO(
        @Schema(description = "When the dashboard was built (UTC)")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant observedAt,
        @Schema(description = "Data-quality findings per rule")
        Quality quality,
        @Schema(description = "Properties with at least one recorded row per source")
        Coverage coverage,
        @Schema(description = "Health of each supported provider, from its recorded jobs")
        List<ProviderHealth> providers,
        @Schema(description = "How the figures are built")
        String note
) {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String HEALTHY = "HEALTHY";
    public static final String DEGRADED = "DEGRADED";

    public static final String NOTE = "Read-only. Quality counts come from the data-quality scan (reused for up to its "
            + "cache period, see quality.generatedAt); coverage counts properties with at least one recorded row per "
            + "source; provider health comes from the latest finished recorded job: HEALTHY if COMPLETED, DEGRADED if "
            + "PARTIAL, FAILED or INTERRUPTED, UNAVAILABLE if none. No provider is called.";

    /** Data-quality findings, or why they are unavailable. */
    public record Quality(
            @Schema(description = "AVAILABLE or UNAVAILABLE", example = AVAILABLE) String status,
            @Schema(description = "When the rule counts were calculated (UTC)")
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant generatedAt,
            @Schema(description = "Issues over all rules; null when unavailable", example = "352") Long totalIssues,
            @Schema(description = "Issues per rule") List<DataQualityRuleSummaryDTO> rules,
            @Schema(description = "Why the block is unavailable; null otherwise") String reason) {}

    /** Source coverage over all properties, or why it is unavailable. */
    public record Coverage(
            @Schema(description = "AVAILABLE or UNAVAILABLE", example = AVAILABLE) String status,
            @Schema(description = "Properties recorded; null when unavailable", example = "1000000") Long totalProperties,
            @Schema(description = "Coverage of each source") List<SourceCoverage> sources,
            @Schema(description = "Why the block is unavailable; null otherwise") String reason) {}

    /** Properties with at least one recorded row of one source. */
    public record SourceCoverage(
            @Schema(description = "Source", example = "SALES") String source,
            @Schema(description = "Properties with at least one row", example = "700000") long propertiesCovered,
            @Schema(description = "propertiesCovered / totalProperties x 100, two decimals", example = "70.00")
            BigDecimal coveragePercent) {}

    /** Health of one provider from its recorded jobs. */
    public record ProviderHealth(
            @Schema(description = "Provider", example = "MIAMI_DADE_API") String provider,
            @Schema(description = "HEALTHY, DEGRADED or UNAVAILABLE", example = HEALTHY) String status,
            @Schema(description = "Status of the latest finished job", example = "COMPLETED") String lastJobStatus,
            @Schema(description = "When the latest finished job ended (UTC)")
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastJobAt,
            @Schema(description = "When a job last completed (UTC); null if never")
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastSuccessAt,
            @Schema(description = "Error code of the latest finished job", example = "RATE_LIMITED") String lastErrorCode,
            @Schema(description = "Jobs queued or running now", example = "0") long activeJobs,
            @Schema(description = "Why the health is unavailable; null otherwise") String reason) {}
}
