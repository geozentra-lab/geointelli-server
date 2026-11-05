package com.geointelli.ai.property.service.service;

import java.util.Optional;
import com.geointelli.ai.property.service.dto.ImageBackfillJobDTO;
import com.geointelli.ai.property.service.dto.ImageBackfillRequest;
import com.geointelli.ai.property.service.dto.ImageBackfillResultDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Manages persisted job progress separately from the background worker. */
public interface ImageBackfillJobService {
    /** Creates a job with an expiring worker lease. */
    Long create(ImageBackfillRequest request);
    /** Marks the job as running with the selected property count. */
    void running(Long id, int total);
    /** Saves a property outcome and updates counters in one transaction. */
    void record(Long jobId, ImageBackfillResultDTO result);
    /** Saves the final status and completion timestamp. */
    void finish(Long id, String status, String errorCode);
    /** Returns a consistent snapshot of progress and property results. */
    Optional<ImageBackfillJobDTO> find(Long id);
    /**
     * Returns a bounded page of jobs without their property results.
     * @param pageable page, size and sort chosen by the caller
     * @return persisted jobs of the requested page
     */
    Page<ImageBackfillJobDTO> list(Pageable pageable);
}
