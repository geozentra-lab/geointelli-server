package com.geointelli.ai.property.service.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.service.IngestionJobService.Counters;

/**
 * What a worker sees of its claimed job: the job snapshot, the current counters and checkpoint, the input
 * files, and the fenced progress writes. {@link #commit} runs a unit of work and the progress update in
 * one transaction, so data is only committed by a worker that still holds the lease.
 */
public class IngestionJobContext {
    private final IngestionJob job;
    private final String workerId;
    private final IngestionJobService jobService;
    private final IngestionInputStore inputStore;
    private final TransactionTemplate transaction;
    private final Duration lease;
    private final Path workDir;
    private final AtomicBoolean leaseActive;
    private Counters counters;
    private String checkpointJson;
    private String step;

    public IngestionJobContext(IngestionJob job, String workerId, IngestionJobService jobService,
            IngestionInputStore inputStore, TransactionTemplate transaction, Duration lease, Path workDir,
            AtomicBoolean leaseActive) {
        this.job = job;
        this.workerId = workerId;
        this.jobService = jobService;
        this.inputStore = inputStore;
        this.transaction = transaction;
        this.lease = lease;
        this.workDir = workDir;
        this.leaseActive = leaseActive;
        this.counters = new Counters(job.getTotal(), job.getSucceeded(), job.getSkipped(), job.getFailed());
        this.checkpointJson = job.getCheckpointJson();
        this.step = job.getCurrentStep();
    }

    public IngestionJob job() { return job; }
    public UUID jobId() { return job.getId(); }
    public Counters counters() { return counters; }
    public String checkpointJson() { return checkpointJson; }
    public String step() { return step; }
    public Path workDir() { return workDir; }

    /** Local path of one of the job's input files, downloaded when the store is remote. */
    public Path input(String name) throws IOException {
        return inputStore.materialize(job.getInputLocation(), name, workDir);
    }

    /** Stops the worker when its thread was interrupted or the heartbeat found the lease gone. */
    public void checkWorker() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (!leaseActive.get()) throw new IngestionLeaseLostException();
    }

    /**
     * Runs the work and records the counters it returns, with the step and checkpoint, in the same
     * transaction. When the lease is gone the transaction is rolled back, so the work is not committed either.
     * @return the recorded counters
     */
    public Counters commit(Supplier<Counters> work, String newStep, String newCheckpointJson)
            throws InterruptedException {
        checkWorker();
        Counters[] recorded = new Counters[1];
        transaction.executeWithoutResult(status -> {
            recorded[0] = work.get();
            if (jobService.recordProgress(jobId(), workerId, recorded[0], newStep, newCheckpointJson, lease) != 1) {
                leaseActive.set(false);
                throw new IngestionLeaseLostException();
            }
        });
        counters = recorded[0];
        step = newStep;
        checkpointJson = newCheckpointJson;
        return counters;
    }

    /** Runs the work and records the given counters in the same transaction. */
    public void commit(Runnable work, Counters newCounters, String newStep, String newCheckpointJson)
            throws InterruptedException {
        commit(() -> { work.run(); return newCounters; }, newStep, newCheckpointJson);
    }

    /** Records progress alone, in its own transaction. */
    public void progress(Counters newCounters, String newStep, String newCheckpointJson) throws InterruptedException {
        commit(() -> newCounters, newStep, newCheckpointJson);
    }

    /** Keeps a failed or skipped record for the detail view; the counters are updated separately. */
    public void item(String itemKey, String status, String reason, String message) {
        jobService.addItem(jobId(), itemKey, status, reason, message);
    }
}
