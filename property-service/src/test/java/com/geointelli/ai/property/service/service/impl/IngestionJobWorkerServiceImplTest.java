package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import com.geointelli.ai.property.service.config.IngestionJobConfig.IngestionWorkerIdentity;
import com.geointelli.ai.property.service.config.IngestionProperties;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionInputStore;
import com.geointelli.ai.property.service.service.IngestionJobService;
import com.geointelli.ai.property.service.service.IngestionJobWorker;
import com.geointelli.ai.property.service.service.IngestionJobWorker.Result;
import com.geointelli.ai.property.service.service.IngestionLeaseLostException;

class IngestionJobWorkerServiceImplTest {
    private IngestionJobService jobService;
    private IngestionInputStore inputStore;
    private IngestionJobWorker jobWorker;
    private ThreadPoolTaskExecutor executor;
    private ScheduledExecutorService heartbeat;
    private IngestionJobWorkerServiceImpl service;
    private IngestionJob job;

    @BeforeEach
    void setUp() {
        jobService = mock(IngestionJobService.class);
        inputStore = mock(IngestionInputStore.class);
        jobWorker = mock(IngestionJobWorker.class);
        when(jobWorker.supports(any())).thenReturn(true);
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.initialize();
        heartbeat = Executors.newSingleThreadScheduledExecutor();
        IngestionProperties properties = new IngestionProperties();
        properties.getJobs().setHeartbeatSeconds(5);
        service = new IngestionJobWorkerServiceImpl(jobService, inputStore, List.of(jobWorker),
            new IngestionWorkerIdentity("host-a-1234", "host-a"), executor, heartbeat,
            mock(PlatformTransactionManager.class), properties);
        job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setJobType(ImportJobType.CSV_IMPORT);
        job.setDataset(IngestionDataset.ADDRESSES);
        job.setStatus("RUNNING");
        job.setInputStorage("S3");
        job.setInputLocation("ingestion/inputs/" + job.getId());
        when(jobService.find(job.getId())).thenReturn(Optional.of(job));
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
        heartbeat.shutdownNow();
    }

    @Test
    @DisplayName("When a job is claimed, then it runs in the background, finishes COMPLETED and its inputs are deleted")
    void whenJobClaimed_thenRunsAndFinishes() throws Exception {
        when(jobService.claimNext("host-a-1234", "host-a", Duration.ofMinutes(10))).thenReturn(Optional.of(job.getId()), Optional.empty());
        when(jobWorker.run(any())).thenReturn(Result.completed());

        assertTrue(service.poll());

        verify(jobService, timeout(5000)).finish(job.getId(), "host-a-1234", "COMPLETED", null, null);
        verify(inputStore, timeout(5000)).deleteAll("ingestion/inputs/" + job.getId());
        verify(jobWorker).run(argThat(context -> context.jobId().equals(job.getId())));
    }

    @Test
    @DisplayName("When a job is running, then polling again claims nothing until it finishes")
    void whenBusy_thenPollDoesNotClaim() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(jobService.claimNext(any(), any(), any())).thenReturn(Optional.of(job.getId()));
        when(jobWorker.run(any())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return Result.completed();
        });

        assertTrue(service.poll());
        assertFalse(service.poll());
        verify(jobService, times(1)).claimNext(any(), any(), any());

        release.countDown();
        verify(jobService, timeout(5000)).finish(eq(job.getId()), eq("host-a-1234"), eq("COMPLETED"), isNull(), isNull());
    }

    @Test
    @DisplayName("When the lease is lost, then the job ends INTERRUPTED and the inputs are kept for a retry")
    void whenLeaseLost_thenInterrupted() throws Exception {
        when(jobWorker.run(any())).thenThrow(new IngestionLeaseLostException());

        service.execute(job.getId());

        verify(jobService).finish(job.getId(), "host-a-1234", "INTERRUPTED", "WORKER_LEASE_LOST", null);
        verify(inputStore, never()).deleteAll(any());
    }

    @Test
    @DisplayName("When the worker throws, then the job ends FAILED with a sanitized message and a failed result is kept as is")
    void whenWorkerThrows_thenFailed() throws Exception {
        when(jobWorker.run(any())).thenThrow(new IllegalStateException("boom at C:\\data\\x.csv"));
        service.execute(job.getId());
        verify(jobService).finish(job.getId(), "host-a-1234", "FAILED", "WORKER_ERROR", "IllegalStateException: boom at <path>");

        doReturn(Result.failed("INVALID_ROW", "addresses.csv:3: bad")).when(jobWorker).run(any());
        service.execute(job.getId());
        verify(jobService).finish(job.getId(), "host-a-1234", "FAILED", "INVALID_ROW", "addresses.csv:3: bad");
        verify(inputStore, never()).deleteAll(any());
    }

    @Test
    @DisplayName("When the heartbeat cannot renew the lease, then the context reports the lease as lost")
    void whenHeartbeatFails_thenLeaseIsLost() throws Exception {
        when(jobService.renewLease(any(), any(), any())).thenReturn(false);
        when(jobWorker.run(any())).thenAnswer(invocation -> {
            com.geointelli.ai.property.service.service.IngestionJobContext context = invocation.getArgument(0);
            Thread.sleep(6500);
            context.checkWorker();
            return Result.completed();
        });

        service.execute(job.getId());

        verify(jobService).finish(job.getId(), "host-a-1234", "INTERRUPTED", "WORKER_LEASE_LOST", null);
    }
}
