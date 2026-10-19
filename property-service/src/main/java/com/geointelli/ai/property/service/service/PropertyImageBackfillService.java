package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ImageBackfillJobDTO;
import com.geointelli.ai.property.service.dto.ImageBackfillRequest;

/** Starts background image backfills and retrieves their persisted progress. */
public interface PropertyImageBackfillService {
    /**
     * Starts a bounded batch for properties without images.
     * @param request county, batch size, and property cursor
     * @return accepted job details
     */
    ImageBackfillJobDTO start(ImageBackfillRequest request);

    /**
     * Returns job progress, including individual property outcomes.
     * @param jobId identifier returned when the job was accepted
     * @return persisted job details
     */
    ImageBackfillJobDTO get(Long jobId);
}
