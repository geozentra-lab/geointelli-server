package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobPageDTO;
import com.geointelli.ai.property.service.enums.ImportJobType;

/** Lets administrators list, inspect and retry persisted import jobs (SCRUM-187, SCRUM-188). */
public interface AdminImportJobService {

    /**
     * Returns a bounded page of tracked import jobs of every type, newest first, without their results.
     * @param page zero-based page number, 0 or greater
     * @param size page size, between 1 and 100
     * @param type only jobs of this type, or null for all
     * @param status only jobs with this status, or null for all
     * @return the requested page plus the import types that are not tracked
     * @throws IllegalArgumentException if page, size or status is out of range
     */
    ImportJobPageDTO listJobs(int page, int size, ImportJobType type, String status);

    /**
     * Returns one tracked import job with its per-record results.
     * @param id numeric Mapillary backfill job ID, or the UUID of an ingestion job
     * @return the job details
     * @throws IllegalArgumentException if the ID is neither a positive number nor a UUID
     * @throws org.springframework.web.server.ResponseStatusException with 404 if no job has this ID
     */
    ImportJobDTO getJob(String id);

    /**
     * Queues a new job that resumes a finished ingestion job.
     * @param id UUID of the ingestion job to resume
     * @param requestedBy caller identity, or null
     * @return the queued job
     * @throws org.springframework.web.server.ResponseStatusException 404 if unknown; 409 when the retry is refused
     */
    ImportJobDTO retryJob(String id, String requestedBy);
}
