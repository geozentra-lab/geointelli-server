package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobPageDTO;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.service.AdminImportJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;

/**
 * Admin view of import jobs (SCRUM-91 / SCRUM-187 / SCRUM-188): Mapillary image backfill jobs (numeric IDs),
 * CSV imports and Miami-Dade ingestion (UUIDs), with retries for the latter two.
 */
@RestController
@RequestMapping("/api/admin/import-jobs")
@RequiredArgsConstructor
@Tag(name = "Admin import jobs", description = "Tracked import jobs of every type, newest first; page >= 0, "
        + "size 1-100, optional type and status filters. Backfill jobs have numeric IDs, ingestion jobs UUIDs. "
        + "Invalid input 400, unknown job 404, refused retry 409.")
public class AdminImportJobController {
    private final AdminImportJobService adminImportJobService;

    /**
     * Lists tracked import jobs.
     *
     * @param page zero-based page number
     * @param size page size, between 1 and 100
     * @param type only jobs of this type
     * @param status only jobs with this status
     * @return 200 with the page and the untracked import types; 400 if page, size or status is out of range
     */
    @GetMapping
    @Operation(summary = "List import jobs", description = "Returns a bounded page of tracked import jobs, newest "
            + "first, with status, counters, current step, error and retry availability, without per-record results. "
            + "untrackedJobTypes lists the legacy imports that run without a job record. No job returns an empty page.")
    public ResponseEntity<ImportJobPageDTO> listJobs(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @Parameter(description = "MAPILLARY_IMAGE_BACKFILL, CSV_IMPORT or PROPERTY_INGESTION")
                                                     @RequestParam(required = false) ImportJobType type,
                                                     @Parameter(description = "QUEUED, RUNNING, COMPLETED, PARTIAL, FAILED or INTERRUPTED")
                                                     @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminImportJobService.listJobs(page, size, type, status));
    }

    /**
     * Returns one tracked import job.
     *
     * @param id numeric backfill job ID or ingestion job UUID
     * @return 200 with the job and its results; 400 if the ID is invalid; 404 if the job does not exist
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get an import job", description = "A numeric ID reads a Mapillary backfill job with its "
            + "per-property results, as GET /api/properties/images/backfill/jobs/{jobId} does. A UUID reads an "
            + "ingestion job with its failed and skipped records. A job whose worker lease expired is shown as "
            + "INTERRUPTED with error code WORKER_LEASE_EXPIRED.")
    public ResponseEntity<ImportJobDTO> getJob(@PathVariable String id) {
        return ResponseEntity.ok(adminImportJobService.getJob(id));
    }

    /**
     * Queues a new job resuming a finished ingestion job.
     *
     * @param id ingestion job UUID
     * @return 202 with the new job; 404 if unknown; 409 with a reason code when the retry is refused
     */
    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry an import job", description = "Creates a new job that resumes a FAILED, PARTIAL or "
            + "INTERRUPTED ingestion job from its checkpoint on the same stored inputs; the response is the new job "
            + "and retryOfJobId points back. Refused with 409 and a reason code: JOB_NOT_FINISHED, JOB_COMPLETED, "
            + "RETRY_ALREADY_ACTIVE, INPUT_ON_OTHER_INSTANCE, INPUT_UNAVAILABLE, INPUT_CHANGED, DATASET_JOB_ACTIVE, "
            + "or RETRY_NOT_SUPPORTED for Mapillary backfill jobs.")
    @ApiResponse(responseCode = "202", description = "Retry queued")
    @ApiResponse(responseCode = "409", description = "Retry refused; the message starts with the reason code")
    public ResponseEntity<ImportJobDTO> retryJob(@PathVariable String id, Principal principal) {
        ImportJobDTO job = adminImportJobService.retryJob(id, principal == null ? null : principal.getName());
        return ResponseEntity.accepted().location(URI.create("/api/admin/import-jobs/" + job.getId())).body(job);
    }
}
