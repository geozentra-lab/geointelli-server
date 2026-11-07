package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.geointelli.ai.property.service.config.IngestionJobConfig.IngestionWorkerIdentity;
import com.geointelli.ai.property.service.config.IngestionProperties;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.IngestionJobStatus;
import com.geointelli.ai.property.service.service.IngestionInputStore;
import com.geointelli.ai.property.service.service.IngestionJobContext;
import com.geointelli.ai.property.service.service.IngestionJobService;
import com.geointelli.ai.property.service.service.IngestionJobWorker;
import com.geointelli.ai.property.service.service.IngestionJobWorkerService;
import com.geointelli.ai.property.service.service.IngestionLeaseLostException;
import lombok.extern.slf4j.Slf4j;

/**
 * Claims jobs with an atomic update, renews the lease on a separate thread every few seconds, and records
 * the final status with the same fencing as every progress write. Lease loss and shutdown end as
 * INTERRUPTED; unexpected errors as FAILED with a sanitized message.
 */
@Service
@Slf4j
public class IngestionJobWorkerServiceImpl implements IngestionJobWorkerService {
    static final String WORKER_LEASE_LOST = "WORKER_LEASE_LOST";
    static final String WORKER_STOPPED = "WORKER_STOPPED";
    static final String WORKER_ERROR = "WORKER_ERROR";
    static final String WORKER_UNAVAILABLE = "WORKER_UNAVAILABLE";

    private final IngestionJobService jobService;
    private final IngestionInputStore inputStore;
    private final List<IngestionJobWorker> workers;
    private final IngestionWorkerIdentity identity;
    private final ThreadPoolTaskExecutor executor;
    private final ScheduledExecutorService heartbeatExecutor;
    private final PlatformTransactionManager transactionManager;
    private final Duration lease;
    private final long heartbeatSeconds;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public IngestionJobWorkerServiceImpl(IngestionJobService jobService, IngestionInputStore inputStore,
            List<IngestionJobWorker> workers, IngestionWorkerIdentity identity,
            @Qualifier("ingestionJobExecutor") ThreadPoolTaskExecutor executor,
            @Qualifier("ingestionHeartbeatExecutor") ScheduledExecutorService heartbeatExecutor,
            PlatformTransactionManager transactionManager, IngestionProperties properties) {
        this.jobService = jobService;
        this.inputStore = inputStore;
        this.workers = workers;
        this.identity = identity;
        this.executor = executor;
        this.heartbeatExecutor = heartbeatExecutor;
        this.transactionManager = transactionManager;
        this.lease = Duration.ofSeconds(properties.getJobs().getLeaseSeconds());
        this.heartbeatSeconds = properties.getJobs().getHeartbeatSeconds();
    }

    @Override
    public boolean poll() {
        if (!busy.compareAndSet(false, true)) return false;
        Optional<UUID> claimed;
        try {
            claimed = jobService.claimNext(identity.workerId(), identity.scope(), lease);
        } catch (RuntimeException error) {
            busy.set(false);
            log.warn("Could not poll for ingestion jobs ({})", error.getClass().getSimpleName());
            return false;
        }
        if (claimed.isEmpty()) {
            busy.set(false);
            return false;
        }
        UUID id = claimed.get();
        try {
            executor.execute(() -> {
                try { execute(id); } finally { busy.set(false); }
            });
        } catch (TaskRejectedException rejected) {
            busy.set(false);
            finish(id, IngestionJobStatus.INTERRUPTED, WORKER_UNAVAILABLE, "The worker on this instance was not available");
        }
        return true;
    }

    @Override
    public void execute(UUID jobId) {
        IngestionJob job = jobService.find(jobId).orElse(null);
        if (job == null) return;
        AtomicBoolean leaseActive = new AtomicBoolean(true);
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleWithFixedDelay(() -> renew(jobId, leaseActive),
            heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
        Path workDir = null;
        IngestionJobWorker.Result result;
        boolean interrupted = false;
        try {
            workDir = Files.createTempDirectory("geozentra-ingestion-");
            IngestionJobContext context = new IngestionJobContext(job, identity.workerId(), jobService, inputStore,
                new TransactionTemplate(transactionManager), lease, workDir, leaseActive);
            IngestionJobWorker worker = workers.stream().filter(candidate -> candidate.supports(job)).findFirst()
                .orElse(null);
            if (worker == null) {
                result = IngestionJobWorker.Result.failed(WORKER_UNAVAILABLE, "No worker handles " + job.getJobType());
            } else {
                log.info("Ingestion job {} ({} {}) started on {}", jobId, job.getJobType(), job.getDataset(), identity.workerId());
                result = worker.run(context);
            }
        } catch (IngestionLeaseLostException lost) {
            result = new IngestionJobWorker.Result(IngestionJobStatus.INTERRUPTED, WORKER_LEASE_LOST, null);
        } catch (InterruptedException stopped) {
            interrupted = true;
            result = new IngestionJobWorker.Result(IngestionJobStatus.INTERRUPTED, WORKER_STOPPED, null);
        } catch (Exception error) {
            log.warn("Ingestion job {} stopped ({})", jobId, error.getClass().getSimpleName());
            result = IngestionJobWorker.Result.failed(WORKER_ERROR,
                CsvIngestionJobWorker.sanitize(error.getClass().getSimpleName() + ": " + error.getMessage()));
        } finally {
            heartbeat.cancel(false);
            if (workDir != null) clean(workDir);
        }
        finish(jobId, result.status(), result.errorCode(), result.errorMessage());
        if (IngestionJobStatus.COMPLETED.equals(result.status()) && !"NONE".equals(job.getInputStorage())) {
            try { inputStore.deleteAll(job.getInputLocation()); }
            catch (RuntimeException error) { log.warn("Could not delete the inputs of ingestion job {}", jobId); }
        }
        log.info("Ingestion job {} finished with {} {}", jobId, result.status(), result.errorCode() == null ? "" : result.errorCode());
        if (interrupted) Thread.currentThread().interrupt();
    }

    private void renew(UUID jobId, AtomicBoolean leaseActive) {
        try {
            if (!jobService.renewLease(jobId, identity.workerId(), lease)) leaseActive.set(false);
        } catch (RuntimeException error) {
            leaseActive.set(false);
            log.warn("Ingestion job {} could not renew its lease", jobId);
        }
    }

    private void finish(UUID jobId, String status, String errorCode, String errorMessage) {
        try {
            jobService.finish(jobId, identity.workerId(), status, errorCode, errorMessage);
        } catch (RuntimeException error) {
            log.warn("Could not save the final status of ingestion job {}", jobId);
        }
    }

    private void clean(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException error) {
            log.warn("Could not remove a temporary ingestion working directory");
        }
    }
}
