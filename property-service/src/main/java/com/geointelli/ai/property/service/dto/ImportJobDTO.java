package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * One import job as seen by administrators and by whoever started it (SCRUM-187, SCRUM-188).
 * Mapillary backfill jobs keep their numeric {@code jobId}; CSV and property ingestion jobs have a UUID in
 * {@code id} and no {@code jobId}. Results and items are only filled in the detail views.
 */
@Data
public class ImportJobDTO {
    @Schema(description = "Job identifier: the numeric backfill job ID as text, or the UUID of an ingestion job",
        example = "4f1c7d2e-9b2a-4c1e-8f3a-2d9e6b7c1a55")
    private String id;
    @Schema(description = "Import job type", example = "CSV_IMPORT")
    private ImportJobType jobType;
    @Schema(description = "What the job loads; null for Mapillary backfill jobs", example = "ADDRESSES")
    private IngestionDataset dataset;
    @Schema(description = "Numeric Mapillary backfill job ID; null for CSV and property ingestion jobs", example = "7")
    private Long jobId;
    @Schema(description = "County processed by the job; null until a CSV import resolved it", example = "1")
    private Long countyId;
    @Schema(description = "County and state given to a CSV import", example = "Miami-Dade")
    private String sourceCounty;
    private String sourceState;
    @Schema(description = "Job status: QUEUED, RUNNING, COMPLETED, PARTIAL, FAILED or INTERRUPTED", example = "COMPLETED")
    private String status;
    @Schema(description = "Records the job will handle: CSV rows (counted file by file) or folios", example = "20")
    private int total;
    @Schema(description = "Records already handled (completed + skipped + failed)", example = "20")
    private int processed;
    @Schema(description = "Records written to the database", example = "15")
    private int completed;
    @Schema(description = "Records intentionally not written, for example a folio that already exists", example = "4")
    private int skipped;
    @Schema(description = "Records that failed", example = "1")
    private int failed;
    @Schema(description = "File or phase being processed", example = "addresses.csv")
    private String currentStep;
    @Schema(description = "Error code of a failed or interrupted job; null when there is none", example = "INVALID_ROW")
    private String errorCode;
    @Schema(description = "Short explanation of the error without server paths or credentials")
    private String errorMessage;
    @Schema(description = "Where the inputs are kept: S3, LOCAL or NONE", example = "S3")
    private String inputStorage;
    @Schema(description = "1 for a new job, one more for each retry", example = "1")
    private Integer attempt;
    @Schema(description = "Job this one resumed; null for a first attempt")
    private String retryOfJobId;
    @Schema(description = "Whether POST /api/admin/import-jobs/{id}/retry is expected to succeed")
    private Boolean retryable;
    @Schema(description = "Why a retry is refused; null when retryable", example = "RETRY_ALREADY_ACTIVE")
    private String retryBlockedReason;
    @Schema(description = "When the job was created (UTC)", example = "2026-11-05T10:00:00Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    @Schema(description = "When a worker claimed the job (UTC); null while queued")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startedAt;
    @Schema(description = "When the job finished (UTC); null while it is queued or running")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant finishedAt;

    @Schema(description = "Per-property outcomes of a Mapillary backfill job; only returned by the detail endpoint")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ImageBackfillResultDTO> results;

    @Schema(description = "Failed and skipped records of an ingestion job (first 1000); only returned by the detail endpoints")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<IngestionJobItemDTO> items;
}
