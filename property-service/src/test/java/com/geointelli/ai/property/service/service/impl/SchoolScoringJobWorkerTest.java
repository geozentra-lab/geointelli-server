package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionInputStore;
import com.geointelli.ai.property.service.service.IngestionJobContext;
import com.geointelli.ai.property.service.service.IngestionJobService;
import com.geointelli.ai.property.service.service.IngestionJobService.Counters;
import com.geointelli.ai.property.service.service.IngestionJobWorker.Result;
import com.geointelli.ai.property.service.service.ReferenceDataScopeService;
import com.geointelli.ai.property.service.service.SchoolAssignmentService;
import com.geointelli.ai.property.service.service.SchoolAssignmentService.AssignmentResult;
import com.geointelli.ai.property.service.service.SchoolAssignmentService.LevelCount;
import com.geointelli.ai.property.service.service.SchoolAssignmentService.SchoolCounty;
import com.geointelli.ai.property.service.service.SchoolScoreV2Service;
import com.geointelli.ai.property.service.service.SchoolScoreV2Service.ScoreResult;

/** School scoring jobs (SCRUM-203): county by county, each step committed with the job's counters. */
class SchoolScoringJobWorkerTest {
    @TempDir Path directory;
    private SchoolAssignmentService assignments;
    private SchoolScoreV2Service scores;
    private ReferenceDataScopeService scopes;
    private IngestionJobService jobService;
    private PlatformTransactionManager transactionManager;
    private SchoolScoringJobWorker worker;
    private IngestionJob job;
    private final SchoolCounty miamiDade = new SchoolCounty(1L, "12086", "Miami-Dade", "FL");
    private final SchoolCounty alachua = new SchoolCounty(2L, "12001", "Alachua", "FL");

    @BeforeEach
    void setUp() {
        assignments = mock(SchoolAssignmentService.class);
        scores = mock(SchoolScoreV2Service.class);
        scopes = mock(ReferenceDataScopeService.class);
        when(scopes.countyFips(1L)).thenReturn(Optional.of("12086"));
        jobService = mock(IngestionJobService.class);
        when(jobService.recordProgress(any(), anyString(), any(), any(), any(), any())).thenReturn(1);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenAnswer(invocation -> mock(TransactionStatus.class));
        worker = new SchoolScoringJobWorker(assignments, scores, scopes);
        County county = new County();
        county.setId(1L);
        job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setJobType(ImportJobType.SCHOOL_SCORING);
        job.setDataset(IngestionDataset.SCHOOL_ASSIGNMENT);
        job.setCounty(county);
        job.setSourceCounty("Miami-Dade");
        job.setSourceState("FL");
        job.setInputStorage("NONE");
        job.setStatus("RUNNING");
    }

    private IngestionJobContext context() {
        IngestionInputStore store = new IngestionInputStore() {
            @Override public String storageType() { return "NONE"; }
            @Override public String scope() { return "test"; }
            @Override public String locationFor(UUID jobId) { return null; }
            @Override public StoredInput store(String location, String name, InputStream content, long size) { throw new UnsupportedOperationException(); }
            @Override public Optional<StoredInput> describe(String location, String name) { return Optional.empty(); }
            @Override public Path materialize(String location, String name, Path workDir) { throw new UnsupportedOperationException(); }
            @Override public void deleteAll(String location) { }
        };
        return new IngestionJobContext(job, "worker-1", jobService, store, new TransactionTemplate(transactionManager),
            Duration.ofMinutes(10), directory, new AtomicBoolean(true));
    }

    private static AssignmentResult assigned(String fips5, int properties, int assigned) {
        return new AssignmentResult(fips5, properties, assigned, List.of(new LevelCount("ELEMENTARY", "ZONE", assigned - 1),
            new LevelCount("ELEMENTARY", "NEAREST", 1)), assigned, 0, 0, 10);
    }

    private ArgumentCaptor<Counters> counters() {
        ArgumentCaptor<Counters> counters = ArgumentCaptor.forClass(Counters.class);
        verify(jobService, atLeastOnce()).recordProgress(eq(job.getId()), eq("worker-1"), counters.capture(), any(), any(), any());
        return counters;
    }

    @Test
    @DisplayName("Only school scoring jobs are handled")
    void supportsSchoolScoringOnly() {
        assertTrue(worker.supports(job));
        job.setJobType(ImportJobType.REFERENCE_DATA_IMPORT);
        assertFalse(worker.supports(job));
    }

    @Test
    @DisplayName("An assignment job assigns the county, then scores it, each in its own transaction")
    void assignThenScore() throws Exception {
        when(assignments.countiesToAssign("12086")).thenReturn(List.of(miamiDade));
        when(assignments.countPropertiesToAssign(miamiDade)).thenReturn(10);
        when(assignments.assignCounty("12086")).thenReturn(assigned("12086", 10, 9));
        when(scores.scoreCounty("12086")).thenReturn(new ScoreResult("12086", 2026, 9, 9, 9, 0, 0, 5));

        Result result = worker.run(context());

        assertEquals("COMPLETED", result.status());
        var inOrder = inOrder(assignments, scores);
        inOrder.verify(assignments).assignCounty("12086");
        inOrder.verify(scores).scoreCounty("12086");
        Counters last = counters().getValue();
        assertEquals(new Counters(10, 9, 1, 0), last, "9 assigned, 1 with a parcel but no school");
        // start, assignment, score and summary: each a committed transaction
        verify(transactionManager, times(4)).commit(any());
        ArgumentCaptor<String> steps = ArgumentCaptor.forClass(String.class);
        verify(jobService, atLeastOnce()).recordProgress(any(), any(), any(), steps.capture(), any(), any());
        assertEquals("12086 E 8/0/1 M 0/0/0 H 0/0/0 (zone/unmatched/nearest), scored 9", steps.getValue());
    }

    @Test
    @DisplayName("A score job without a county scores every assigned county and counts the scored properties")
    void scoreEveryCounty() throws Exception {
        job.setDataset(IngestionDataset.SCHOOL_SCORE_V2);
        job.setCounty(null);
        when(scores.countiesToScore(null)).thenReturn(List.of(alachua, miamiDade));
        when(scores.countPropertiesToScore(alachua)).thenReturn(3);
        when(scores.countPropertiesToScore(miamiDade)).thenReturn(7);
        when(scores.scoreCounty("12001")).thenReturn(new ScoreResult("12001", 2026, 3, 2, 2, 0, 0, 1));
        when(scores.scoreCounty("12086")).thenReturn(new ScoreResult("12086", 2026, 7, 7, 0, 7, 0, 1));

        Result result = worker.run(context());

        assertEquals("COMPLETED", result.status());
        verify(assignments, never()).assignCounty(any());
        assertEquals(new Counters(10, 9, 1, 0), counters().getValue());
    }

    @Test
    @DisplayName("A database error fails the job at its county and keeps a failed item")
    void databaseErrorFails() throws Exception {
        when(assignments.countiesToAssign("12086")).thenReturn(List.of(miamiDade));
        when(assignments.countPropertiesToAssign(miamiDade)).thenReturn(10);
        when(assignments.assignCounty("12086")).thenThrow(new DataIntegrityViolationException("fk violation"));

        Result result = worker.run(context());

        assertEquals("FAILED", result.status());
        assertEquals(SchoolScoringJobWorker.DATABASE_ERROR, result.errorCode());
        assertTrue(result.errorMessage().startsWith("12086: "));
        verify(jobService).addItem(eq(job.getId()), eq("12086"), eq("FAILED"), eq(SchoolScoringJobWorker.DATABASE_ERROR), any());
        verify(scores, never()).scoreCounty(any());
        verify(transactionManager).rollback(any());
    }

    @Test
    @DisplayName("A county without zones any more, or without a FIPS code, fails without writing")
    void nothingToDo() throws Exception {
        when(assignments.countiesToAssign("12086")).thenReturn(List.of());
        assertEquals(SchoolScoringJobWorker.NOTHING_TO_SCORE, worker.run(context()).errorCode());

        when(scopes.countyFips(1L)).thenReturn(Optional.empty());
        assertEquals(SchoolScoringJobWorker.SCOPE_UNAVAILABLE, worker.run(context()).errorCode());
        verify(assignments, never()).assignCounty(any());
    }
}
