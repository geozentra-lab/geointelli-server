package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.geointelli.ai.property.service.enums.ImportJobType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** One import job as seen by administrators (SCRUM-187); results are only filled in the detail view. */
@Data
public class ImportJobDTO {
    @Schema(description = "Import job type; only tracked types are listed", example = "MAPILLARY_IMAGE_BACKFILL")
    private ImportJobType jobType;
    @Schema(description = "Job ID, identical to the Mapillary backfill job ID", example = "7")
    private Long jobId;
    @Schema(description = "County processed by the job", example = "1")
    private Long countyId;
    @Schema(description = "Job status: QUEUED, RUNNING, COMPLETED, PARTIAL, FAILED or INTERRUPTED", example = "COMPLETED")
    private String status;
    @Schema(description = "Number of properties selected for the job", example = "20")
    private int total;
    @Schema(description = "Properties already handled (completed + skipped + failed)", example = "20")
    private int processed;
    @Schema(description = "Properties that received an image", example = "15")
    private int completed;
    @Schema(description = "Properties skipped, for example without a suitable image", example = "4")
    private int skipped;
    @Schema(description = "Properties that failed", example = "1")
    private int failed;
    @Schema(description = "Error code of a failed or interrupted job; null when there is none", example = "MAPILLARY_API_ERROR")
    private String errorCode;
    @Schema(description = "When the job was created (UTC)", example = "2026-11-05T10:00:00Z")
    private Instant createdAt;
    @Schema(description = "When the job finished (UTC); null while it is queued or running")
    private Instant finishedAt;

    @Schema(description = "Per-property outcomes; only returned by the detail endpoint")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ImageBackfillResultDTO> results;
}
