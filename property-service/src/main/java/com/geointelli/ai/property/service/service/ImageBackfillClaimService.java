package com.geointelli.ai.property.service.service;

import java.util.List;
import com.geointelli.ai.property.service.dto.ImageBackfillRequest;

/** Assigns distinct properties to jobs and maintains their expiring ownership. */
public interface ImageBackfillClaimService {
    /** Claims an available batch in a short transaction, skipping locked properties. */
    List<Long> claim(Long jobId, ImageBackfillRequest request);
    /** Renews a live job and its pending claims; expired jobs cannot regain ownership. */
    boolean renew(Long jobId);
    /** Checks ownership before starting expensive image processing. */
    boolean owns(Long jobId, Long propertyId);
    /** Locks a live claim until the caller's image-saving transaction completes. */
    boolean lockOwned(Long jobId, Long propertyId);
    /** Releases remaining claims when a job ends. */
    void release(Long jobId);
    /** Marks jobs whose heartbeat expired as interrupted. */
    void interruptExpired();
}
