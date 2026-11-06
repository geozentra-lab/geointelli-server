package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobPageDTO;

/** Lets administrators list and inspect persisted import jobs (SCRUM-187). */
public interface AdminImportJobService {

    /**
     * Returns a bounded page of tracked import jobs, newest first, without their property results.
     * @param page zero-based page number, 0 or greater
     * @param size page size, between 1 and 100
     * @return the requested page plus the import types that are not tracked
     * @throws IllegalArgumentException if page or size is out of range
     */
    ImportJobPageDTO listJobs(int page, int size);

    /**
     * Returns one tracked import job with its per-property results.
     * @param id job ID, the same as the Mapillary backfill job ID
     * @return the job details
     * @throws IllegalArgumentException if the ID is null or not positive
     * @throws org.springframework.web.server.ResponseStatusException with 404 if no job has this ID
     */
    ImportJobDTO getJob(Long id);
}
