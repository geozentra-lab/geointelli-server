package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobPageDTO;
import com.geointelli.ai.property.service.service.AdminImportJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin view of import jobs (SCRUM-91 / SCRUM-187). It reads the persisted Mapillary image backfill jobs; CSV
 * imports and property ingestion run without a job record and are reported as untracked.
 */
@RestController
@RequestMapping("/api/admin/import-jobs")
@RequiredArgsConstructor
@Tag(name = "Admin import jobs", description = "Tracked import jobs, newest first; page >= 0, size 1-100. "
        + "Only MAPILLARY_IMAGE_BACKFILL jobs are persisted. Invalid input 400, unknown job 404.")
public class AdminImportJobController {
    private final AdminImportJobService adminImportJobService;

    /**
     * Lists tracked import jobs.
     *
     * @param page zero-based page number
     * @param size page size, between 1 and 100
     * @return 200 with the page and the untracked import types; 400 if page or size is out of range
     */
    @GetMapping
    @Operation(summary = "List import jobs", description = "Returns a bounded page of tracked import jobs, newest "
            + "first, with status, counters, error code and timestamps, without per-property results. "
            + "untrackedJobTypes lists the legacy imports that run without a job record. No job returns an empty page.")
    public ResponseEntity<ImportJobPageDTO> listJobs(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminImportJobService.listJobs(page, size));
    }

    /**
     * Returns one tracked import job.
     *
     * @param id job ID
     * @return 200 with the job and its results; 400 if the ID is invalid; 404 if the job does not exist
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get an import job", description = "Uses the same ID and statuses as "
            + "GET /api/properties/images/backfill/jobs/{jobId} and includes the per-property results. "
            + "A job whose worker lease expired is shown as INTERRUPTED with error code WORKER_LEASE_EXPIRED.")
    public ResponseEntity<ImportJobDTO> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(adminImportJobService.getJob(id));
    }
}
