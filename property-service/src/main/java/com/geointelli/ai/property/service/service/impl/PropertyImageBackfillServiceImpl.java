package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.core.task.AsyncTaskExecutor;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.*;
import com.geointelli.ai.property.service.integration.mapillary.MapillaryApiClient;
import com.geointelli.ai.property.service.repository.CountyRepository;
import com.geointelli.ai.property.service.service.*;
import lombok.extern.slf4j.Slf4j;

/** Runs background batches whose property claims are shared across backend instances. */
@Service
@Slf4j
public class PropertyImageBackfillServiceImpl implements PropertyImageBackfillService {
    private final ImageBackfillJobService jobs;
    private final ImageBackfillClaimService claims;
    private final CountyRepository counties;
    private final MapillaryApiClient api;
    private final MapillaryImageSelectionService selection;
    private final MapillaryImagePersistenceService persistence;
    private final Executor executor;
    private final AsyncTaskExecutor propertyExecutor;
    private final ScheduledExecutorService heartbeatExecutor;
    private final DataSource dataSource;

    public PropertyImageBackfillServiceImpl(ImageBackfillJobService jobs, ImageBackfillClaimService claims,
            CountyRepository counties, MapillaryApiClient api, MapillaryImageSelectionService selection,
            MapillaryImagePersistenceService persistence, @Qualifier("imageBackfillExecutor") Executor executor,
            @Qualifier("imageBackfillHeartbeatExecutor") ScheduledExecutorService heartbeatExecutor, DataSource dataSource,
            @Qualifier("imageBackfillPropertyExecutor") AsyncTaskExecutor propertyExecutor) {
        this.jobs = jobs; this.claims = claims; this.counties = counties;
        this.api = api; this.selection = selection; this.persistence = persistence; this.executor = executor;
        this.heartbeatExecutor = heartbeatExecutor; this.dataSource = dataSource; this.propertyExecutor = propertyExecutor;
    }

    @Override
    public ImageBackfillJobDTO start(ImageBackfillRequest request) {
        if (!counties.existsById(request.getCountyId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "County not found");
        if (!api.isConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MAPILLARY_ACCESS_TOKEN is not configured");
        Long id = null;
        try {
            Long createdId = jobs.create(request);
            id = createdId;
            ImageBackfillJobDTO accepted = get(createdId);
            executor.execute(() -> run(createdId, request));
            return accepted;
        } catch (RuntimeException error) {
            if (id != null) {
                finishFailed(id, "FAILED", "JOB_START_FAILED");
                release(id);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Could not start the job; the worker on this instance may already be busy");
        }
    }

    @Override
    public ImageBackfillJobDTO get(Long jobId) {
        claims.interruptExpired();
        return jobs.find(jobId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image backfill job not found"));
    }

    private void run(Long id, ImageBackfillRequest request) {
        Path directory = null;
        ScheduledFuture<?> heartbeat = null;
        AtomicBoolean leaseActive = new AtomicBoolean(true);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        List<Future<PropertyTaskResult>> tasks = new ArrayList<>();
        BatchOutcome outcome = new BatchOutcome("FAILED", "WORKER_ERROR", false);
        boolean tasksFinished = false;
        try {
            var ids = claims.claim(id, request);
            jobs.running(id, ids.size());
            log.info("Image backfill {} claimed {} properties for county {}", id, ids.size(), request.getCountyId());
            heartbeat = heartbeatExecutor.scheduleWithFixedDelay(() -> renew(id, leaseActive), 30, 30, TimeUnit.SECONDS);
            directory = Files.createTempDirectory("geozentra-mapillary-");
            Path workingDirectory = directory;
            for (long propertyId : ids) {
                checkWorker(leaseActive);
                tasks.add(propertyExecutor.submit(() -> {
                    try { return processProperty(id, propertyId, workingDirectory, leaseActive, stopRequested); }
                    catch (RuntimeException | Error failure) { stopRequested.set(true); throw failure; }
                }));
            }
            outcome = awaitTasks(tasks, stopRequested, leaseActive);
            tasksFinished = true;
        } catch (InterruptedException interrupted) {
            outcome = new BatchOutcome("INTERRUPTED", "WORKER_STOPPED", true);
        } catch (WorkerLeaseLostException lost) {
            outcome = new BatchOutcome("INTERRUPTED", "WORKER_LEASE_LOST", false);
        } catch (Exception error) {
            log.warn("Image backfill {} stopped ({})", id, error.getClass().getSimpleName());
        } finally {
            stopRequested.set(true);
            if (!tasksFinished) {
                // Wait for real task completion: cancellation alone does not mean an upload stopped.
                BatchOutcome remaining = awaitTasks(tasks, stopRequested, leaseActive);
                if (remaining.interrupted()) outcome = new BatchOutcome("INTERRUPTED", "WORKER_STOPPED", true);
            }
            if (heartbeat != null) heartbeat.cancel(false);
            finishFailed(id, outcome.status(), outcome.errorCode());
            release(id);
            if (directory != null) clean(directory);
            if (outcome.interrupted()) Thread.currentThread().interrupt();
        }
    }

    /** Each task has its own connection and working folder; no JPA entities cross threads. */
    private PropertyTaskResult processProperty(Long id, long propertyId, Path directory,
            AtomicBoolean leaseActive, AtomicBoolean stopRequested) {
        Path propertyDirectory = null;
        try {
            if (stopRequested.get()) return null;
            checkWorker(leaseActive);
            if (!claims.owns(id, propertyId)) {
                jobs.record(id, new ImageBackfillResultDTO(propertyId, "SKIPPED", "CLAIM_LOST", null));
                return new PropertyTaskResult(false, null);
            }
            propertyDirectory = Files.createDirectory(directory.resolve("property-" + propertyId));
            MapillarySelectionDTO selected;
            try (Connection connection = dataSource.getConnection()) {
                connection.setReadOnly(true);
                selected = selection.select(connection, propertyId, propertyDirectory);
            }
            checkWorker(leaseActive);
            if (stopRequested.get()) return null;
            ImageBackfillResultDTO result = selected == null
                ? new ImageBackfillResultDTO(propertyId, "SKIPPED", "NO_SUITABLE_IMAGE_OR_PARCEL", null)
                : persistence.save(id, propertyId, selected);
            jobs.record(id, result);
            Thread.sleep(1000);
            return new PropertyTaskResult(false, null);
        } catch (MapillaryApiClient.ApiException providerError) {
            stopRequested.set(true);
            jobs.record(id, new ImageBackfillResultDTO(propertyId, "FAILED", "MAPILLARY_API_ERROR", null));
            return new PropertyTaskResult(true, "MAPILLARY_API_ERROR");
        } catch (InterruptedException interrupted) {
            stopRequested.set(true);
            Thread.currentThread().interrupt();
            return new PropertyTaskResult(true, "WORKER_STOPPED");
        } catch (WorkerLeaseLostException lost) {
            stopRequested.set(true);
            return new PropertyTaskResult(true, "WORKER_LEASE_LOST");
        } catch (SQLException | DataAccessException databaseError) {
            stopRequested.set(true);
            return new PropertyTaskResult(true, "DATABASE_ERROR");
        } catch (Exception propertyError) {
            log.warn("Image backfill {} property {} failed ({})", id, propertyId, propertyError.getClass().getSimpleName());
            jobs.record(id, new ImageBackfillResultDTO(propertyId, "FAILED", "IMAGE_PROCESSING_OR_STORAGE_FAILED", null));
            return new PropertyTaskResult(true, null);
        } finally {
            if (propertyDirectory != null) clean(propertyDirectory);
        }
    }

    /** Drains every submitted task before the job can finish or release its claims. */
    private BatchOutcome awaitTasks(List<Future<PropertyTaskResult>> tasks, AtomicBoolean stopRequested,
            AtomicBoolean leaseActive) {
        int failures = 0;
        String errorCode = null;
        boolean interrupted = false;
        for (Future<PropertyTaskResult> task : tasks) {
            boolean finished = false;
            while (!finished) {
                try {
                    PropertyTaskResult result = task.get();
                    if (result != null) {
                        if (result.failed()) failures++;
                        if (errorCode == null && result.errorCode() != null) errorCode = result.errorCode();
                    }
                    finished = true;
                } catch (InterruptedException stopped) {
                    interrupted = true;
                    stopRequested.set(true);
                    // get() clears interruption; continue waiting so active tasks cannot outlive the job.
                } catch (ExecutionException | CancellationException error) {
                    stopRequested.set(true);
                    if (errorCode == null) errorCode = "WORKER_ERROR";
                    finished = true;
                }
            }
        }
        if (interrupted || "WORKER_STOPPED".equals(errorCode)) {
            return new BatchOutcome("INTERRUPTED", "WORKER_STOPPED", interrupted);
        }
        if (!leaseActive.get() || "WORKER_LEASE_LOST".equals(errorCode)) {
            return new BatchOutcome("INTERRUPTED", "WORKER_LEASE_LOST", false);
        }
        if (errorCode != null) return new BatchOutcome("FAILED", errorCode, false);
        return new BatchOutcome(failures == 0 ? "COMPLETED" : "PARTIAL", null, false);
    }

    private record PropertyTaskResult(boolean failed, String errorCode) { }
    private record BatchOutcome(String status, String errorCode, boolean interrupted) { }

    private void renew(Long id, AtomicBoolean leaseActive) {
        try {
            if (!claims.renew(id)) leaseActive.set(false);
        } catch (RuntimeException error) {
            leaseActive.set(false);
            log.warn("Image backfill {} could not renew its claims", id);
        }
    }

    private void checkWorker(AtomicBoolean leaseActive) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (!leaseActive.get()) throw new WorkerLeaseLostException();
    }

    private void finishFailed(Long id, String status, String reason) {
        try { jobs.finish(id, status, reason); }
        catch (RuntimeException error) { log.warn("Could not save final progress for image backfill {}", id); }
    }

    private void release(Long id) {
        try { claims.release(id); }
        catch (RuntimeException error) { log.warn("Could not release claims for image backfill {}; they will expire", id); }
    }

    private void clean(Path directory) {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException error) { log.warn("Could not remove a temporary Mapillary working directory"); }
    }

    private static class WorkerLeaseLostException extends RuntimeException { }
}
