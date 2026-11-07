package com.geointelli.ai.property.service.service;

import java.util.UUID;

/** Claims queued ingestion jobs for this instance and runs them one at a time. */
public interface IngestionJobWorkerService {

    /**
     * Interrupts expired jobs, then claims and starts the next runnable job when this instance is idle.
     * @return whether a job was started
     */
    boolean poll();

    /** Runs an already claimed job on the calling thread, with a heartbeat renewing its lease. */
    void execute(UUID jobId);
}
