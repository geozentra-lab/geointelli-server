package com.geointelli.ai.property.service.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.geointelli.ai.property.service.dto.IngestionJobItemDTO;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.IngestionDataset;

/**
 * Persists ingestion jobs and their progress (SCRUM-188). Writes made while a job runs are fenced by the
 * worker ID and the lease: they update nothing once the lease expired or another worker owns the job.
 */
public interface IngestionJobService {

    /** Progress counters; {@code processed} is always {@code succeeded + skipped + failed}. */
    record Counters(int total, int succeeded, int skipped, int failed) {
        public int processed() { return succeeded + skipped + failed; }

        public Counters plus(int succeededDelta, int skippedDelta, int failedDelta) {
            return new Counters(total, succeeded + succeededDelta, skipped + skippedDelta, failed + failedDelta);
        }

        public Counters withTotal(int newTotal) { return new Counters(newTotal, succeeded, skipped, failed); }
    }

    /** Inserts a new job; its status must be QUEUED. */
    IngestionJob create(IngestionJob job);

    Optional<IngestionJob> find(UUID id);

    /**
     * Interrupts expired jobs, then claims the oldest queued job this instance may run.
     * @param scope instance scope; jobs with local inputs are only claimed by their own instance
     * @return the claimed job ID, if any
     */
    Optional<UUID> claimNext(String workerId, String scope, Duration lease);

    /** Extends the lease; false when the job is no longer owned by this worker. */
    boolean renewLease(UUID id, String workerId, Duration lease);

    /**
     * Writes counters, the current step and the checkpoint, joining the caller's transaction so data and
     * progress commit together.
     * @return rows updated: 0 means the lease was lost and the caller must roll back and stop
     */
    int recordProgress(UUID id, String workerId, Counters counters, String step, String checkpointJson,
            Duration lease);

    /** Records the final status; ignored when another worker owns the job. */
    void finish(UUID id, String workerId, String status, String errorCode, String errorMessage);

    /** Keeps a failed or skipped record, up to the configured cap per job. */
    void addItem(UUID id, String itemKey, String status, String reason, String message);

    /** Marks running jobs with an expired lease, and unclaimed local-input jobs past their deadline, INTERRUPTED. */
    int interruptExpired();

    boolean hasActive(IngestionDataset dataset);

    boolean hasActiveRetry(UUID jobId);

    List<IngestionJobItemDTO> items(UUID id, int limit);
}
