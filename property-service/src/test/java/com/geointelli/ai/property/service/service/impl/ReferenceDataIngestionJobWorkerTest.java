package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionInputStore;
import com.geointelli.ai.property.service.service.IngestionJobContext;
import com.geointelli.ai.property.service.service.IngestionJobService;
import com.geointelli.ai.property.service.service.IngestionJobService.Counters;
import com.geointelli.ai.property.service.service.IngestionJobWorker.Result;
import com.geointelli.ai.property.service.service.IngestionLeaseLostException;
import com.geointelli.ai.property.service.service.ReferenceDataImportService;
import com.geointelli.ai.property.service.service.ReferenceDataImportService.InvalidReferenceRowException;
import com.geointelli.ai.property.service.service.ReferenceDataImportService.LoadRequest;
import com.geointelli.ai.property.service.service.ReferenceDataImportService.LoadResult;
import com.geointelli.ai.property.service.service.ReferenceDataScopeService;

/** One reference file per job, loaded together with the job's counters and checkpoint (SCRUM-201). */
class ReferenceDataIngestionJobWorkerTest {
    @TempDir Path directory;
    private ReferenceDataImportService importer;
    private ReferenceDataScopeService scopes;
    private IngestionJobService jobService;
    private PlatformTransactionManager transactionManager;
    private ReferenceDataIngestionJobWorker worker;
    private IngestionJob job;

    @BeforeEach
    void setUp() throws Exception {
        importer = mock(ReferenceDataImportService.class);
        when(importer.countRows(any())).thenReturn(3637L);
        scopes = mock(ReferenceDataScopeService.class);
        when(scopes.countyFips(1L)).thenReturn(Optional.of("12086"));
        when(scopes.stateFips("FL")).thenReturn(Optional.of("12"));
        jobService = mock(IngestionJobService.class);
        when(jobService.recordProgress(any(), anyString(), any(), any(), any(), any())).thenReturn(1);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenAnswer(invocation -> mock(TransactionStatus.class));
        worker = new ReferenceDataIngestionJobWorker(importer, scopes, new ObjectMapper());
        County county = new County();
        county.setId(1L);
        job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setJobType(ImportJobType.REFERENCE_DATA_IMPORT);
        job.setDataset(IngestionDataset.FLOOD_ZONES);
        job.setCounty(county);
        job.setSourceCounty("Miami-Dade");
        job.setSourceState("FL");
        job.setInputStorage("LOCAL");
        job.setInputLocation("inputs");
        job.setStatus("RUNNING");
        job.setParametersJson("{\"inputs\":[{\"name\":\"12086_miami-dade.csv\",\"size\":10,\"etag\":\"e\"}],"
            + "\"downloadedOn\":\"2026-11-14\"}");
    }

    private IngestionJobContext context() {
        IngestionInputStore store = new IngestionInputStore() {
            @Override public String storageType() { return "LOCAL"; }
            @Override public String scope() { return "test"; }
            @Override public String locationFor(UUID jobId) { return "inputs"; }
            @Override public StoredInput store(String location, String name, InputStream content, long size) { throw new UnsupportedOperationException(); }
            @Override public Optional<StoredInput> describe(String location, String name) { return Optional.empty(); }
            @Override public Path materialize(String location, String name, Path workDir) { return directory.resolve(name); }
            @Override public void deleteAll(String location) { }
        };
        return new IngestionJobContext(job, "worker-1", jobService, store, new TransactionTemplate(transactionManager),
            Duration.ofMinutes(10), directory, new AtomicBoolean(true));
    }

    private List<Counters> recordedCounters() {
        ArgumentCaptor<Counters> counters = ArgumentCaptor.forClass(Counters.class);
        verify(jobService, atLeastOnce()).recordProgress(eq(job.getId()), eq("worker-1"), counters.capture(), any(), any(), any());
        return counters.getAllValues();
    }

    @Test
    @DisplayName("Only reference data jobs are handled")
    void supportsReferenceJobsOnly() {
        assertTrue(worker.supports(job));
        job.setJobType(ImportJobType.CSV_IMPORT);
        assertFalse(worker.supports(job));
    }

    @Test
    @DisplayName("A county file is loaded for the county's FIPS code with its vintage, and the job completes with its counts")
    void whenLoaded_thenCompleted() throws Exception {
        when(importer.load(any())).thenReturn(new LoadResult(3637, 3637, 9120, 0));

        Result result = worker.run(context());

        assertEquals("COMPLETED", result.status());
        ArgumentCaptor<LoadRequest> request = ArgumentCaptor.forClass(LoadRequest.class);
        verify(importer).load(request.capture());
        assertEquals("12086", request.getValue().scopeFips());
        assertEquals("12086_miami-dade.csv", request.getValue().sourceFile());
        assertEquals(LocalDate.of(2026, 11, 14), request.getValue().downloadedOn());
        assertEquals(job.getId(), request.getValue().jobId());
        Counters last = recordedCounters().get(recordedCounters().size() - 1);
        assertEquals(new Counters(3637, 3637, 0, 0), last);
        verify(jobService).recordProgress(eq(job.getId()), eq("worker-1"), any(), eq("12086_miami-dade.csv"),
            eq(ReferenceDataIngestionJobWorker.LOADED), any());
        verify(transactionManager, atLeastOnce()).commit(any());
    }

    @Test
    @DisplayName("A statewide file is scoped by the state's FIPS code")
    void whenStatewide_thenStateScope() throws Exception {
        job.setDataset(IngestionDataset.NRI_TRACTS);
        job.setCounty(null);
        when(importer.load(any())).thenReturn(new LoadResult(5114, 5114, 5114, 0));

        assertEquals("COMPLETED", worker.run(context()).status());
        ArgumentCaptor<LoadRequest> request = ArgumentCaptor.forClass(LoadRequest.class);
        verify(importer).load(request.capture());
        assertEquals("12", request.getValue().scopeFips());
    }

    @Test
    @DisplayName("A bad row fails the job with its row number; the file's transaction is rolled back")
    void whenRowInvalid_thenFailedWithItem() throws Exception {
        when(importer.load(any())).thenThrow(new InvalidReferenceRowException(42, "STATIC_BFE is not a number: x"));

        Result result = worker.run(context());

        assertEquals("FAILED", result.status());
        assertEquals("INVALID_ROW", result.errorCode());
        assertTrue(result.errorMessage().startsWith("12086_miami-dade.csv:42: "));
        verify(jobService).addItem(eq(job.getId()), eq("12086_miami-dade.csv:42"), eq("FAILED"), eq("INVALID_ROW"), any());
        verify(transactionManager).rollback(any());
        assertEquals(1, recordedCounters().get(recordedCounters().size() - 1).failed());
    }

    @Test
    @DisplayName("A database error fails the job as DATABASE_ERROR")
    void whenDatabaseFails_thenDatabaseError() throws Exception {
        when(importer.load(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertEquals("DATABASE_ERROR", worker.run(context()).errorCode());
    }

    @Test
    @DisplayName("Without a county FIPS code (migration V42 missing or county unknown) the job fails before reading the file")
    void whenScopeUnavailable_thenFailed() throws Exception {
        when(scopes.countyFips(1L)).thenReturn(Optional.empty());
        assertEquals("SCOPE_UNAVAILABLE", worker.run(context()).errorCode());
        when(scopes.countyFips(1L)).thenThrow(new IllegalStateException("counties.fips_code is missing: apply migration V42 first"));
        assertEquals("SCOPE_UNAVAILABLE", worker.run(context()).errorCode());
        verify(importer, never()).load(any());
    }

    @Test
    @DisplayName("When the lease is lost at commit, the worker stops and nothing is reported as loaded")
    void whenLeaseLost_thenStops() throws Exception {
        when(importer.load(any())).thenReturn(new LoadResult(3637, 3637, 9120, 0));
        when(jobService.recordProgress(any(), anyString(), any(), any(), any(), any())).thenReturn(1, 0);

        assertThrows(IngestionLeaseLostException.class, () -> worker.run(context()));
    }
}
