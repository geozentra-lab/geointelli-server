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
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.geointelli.ai.property.service.config.IngestionProperties;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.CountyService;
import com.geointelli.ai.property.service.service.IngestionInputStore;
import com.geointelli.ai.property.service.service.IngestionJobContext;
import com.geointelli.ai.property.service.service.IngestionJobService;
import com.geointelli.ai.property.service.service.IngestionJobService.Counters;
import com.geointelli.ai.property.service.service.IngestionJobWorker.Result;
import com.geointelli.ai.property.service.service.ParcelService;
import com.geointelli.ai.property.service.service.PropertyIngestionService;
import com.geointelli.ai.property.service.service.PropertyIngestionService.FolioResult;
import com.geointelli.ai.property.service.service.PropertyIngestionService.SourceApiException;
import com.geointelli.ai.property.service.service.PropertyService;

class PropertyApiIngestionJobWorkerTest {
    @TempDir Path directory;
    private PropertyIngestionService ingestionService;
    private ParcelService parcelService;
    private PropertyService propertyService;
    private PropertyRepository propertyRepository;
    private IngestionJobService jobService;
    private PropertyApiIngestionJobWorker worker;
    private IngestionJob job;

    @BeforeEach
    void setUp() {
        ingestionService = mock(PropertyIngestionService.class);
        parcelService = mock(ParcelService.class);
        propertyService = mock(PropertyService.class);
        propertyRepository = mock(PropertyRepository.class);
        CountyService countyService = mock(CountyService.class);
        County miamiDade = new County();
        miamiDade.setId(1L);
        when(countyService.miamiDade()).thenReturn(miamiDade);
        jobService = mock(IngestionJobService.class);
        when(jobService.recordProgress(any(), anyString(), any(), any(), any(), any())).thenReturn(1);
        IngestionProperties properties = new IngestionProperties();
        properties.getJobs().setApiBatchSize(2);
        properties.getJobs().setApiAttempts(2);
        properties.getJobs().setApiBatchPauseMillis(0);
        AsyncTaskExecutor sameThread = Runnable::run;
        worker = new PropertyApiIngestionJobWorker(ingestionService, parcelService, propertyService, propertyRepository,
            countyService, sameThread, properties);
        job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setJobType(ImportJobType.PROPERTY_INGESTION);
        job.setDataset(IngestionDataset.API_PROPERTIES);
        job.setInputStorage("NONE");
        job.setStatus("RUNNING");
    }

    private IngestionJobContext context() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenAnswer(invocation -> mock(TransactionStatus.class));
        IngestionInputStore store = mock(IngestionInputStore.class);
        return new IngestionJobContext(job, "worker-1", jobService, store, new TransactionTemplate(transactionManager),
            Duration.ofMinutes(10), directory, new AtomicBoolean(true));
    }

    private Counters lastCounters() {
        ArgumentCaptor<Counters> counters = ArgumentCaptor.forClass(Counters.class);
        verify(jobService, atLeastOnce()).recordProgress(eq(job.getId()), eq("worker-1"), counters.capture(), any(), any(), any());
        return counters.getValue();
    }

    @Test
    @DisplayName("When properties are ingested, then only parcel folios without a property are selected and outcomes are counted")
    void whenIngestingProperties_thenMissingFoliosAreSelectedAndCounted() throws Exception {
        when(parcelService.getAllFolios(1L)).thenReturn(List.of("A", "B", "C"));
        when(propertyService.getAllFolios(1L)).thenReturn(List.of("B"));
        when(ingestionService.ingestForJob(IngestionDataset.API_PROPERTIES, "A")).thenReturn(FolioResult.written());
        when(ingestionService.ingestForJob(IngestionDataset.API_PROPERTIES, "C")).thenReturn(FolioResult.skipped("PROPERTY_EXISTS"));

        Result result = worker.run(context());

        assertEquals("COMPLETED", result.status());
        assertEquals(new Counters(2, 1, 1, 0), lastCounters());
        verify(ingestionService, never()).ingestForJob(any(), eq("B"));
        verify(jobService).addItem(job.getId(), "C", "SKIPPED", "PROPERTY_EXISTS", null);
        verify(jobService, never()).addItem(any(), eq("A"), any(), any(), any());
    }

    @Test
    @DisplayName("When a folio keeps failing, then it is retried the configured times, recorded and the job ends PARTIAL")
    void whenFolioKeepsFailing_thenRecordedAndPartial() throws Exception {
        job.setDataset(IngestionDataset.API_SALES);
        when(propertyRepository.findFoliosWithoutSales(1L)).thenReturn(List.of("A", "B", "C"));
        when(ingestionService.ingestForJob(IngestionDataset.API_SALES, "A")).thenReturn(FolioResult.written());
        when(ingestionService.ingestForJob(IngestionDataset.API_SALES, "B"))
            .thenThrow(new SourceApiException("SOURCE_API_UNAVAILABLE", "timeout", null));
        when(ingestionService.ingestForJob(IngestionDataset.API_SALES, "C")).thenReturn(FolioResult.written());

        Result result = worker.run(context());

        assertEquals("PARTIAL", result.status());
        assertEquals(new Counters(3, 2, 0, 1), lastCounters());
        verify(ingestionService, times(2)).ingestForJob(IngestionDataset.API_SALES, "B");
        verify(jobService).addItem(job.getId(), "B", "FAILED", "SOURCE_API_UNAVAILABLE", "timeout");
        // selection, then one progress write per batch of two
        verify(jobService, times(3)).recordProgress(eq(job.getId()), eq("worker-1"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("When no folio is missing, then the job completes with zero records")
    void whenNothingMissing_thenCompletesEmpty() throws Exception {
        job.setDataset(IngestionDataset.API_ADDRESSES);
        when(propertyRepository.findFoliosWithoutAddress(1L)).thenReturn(List.of());

        Result result = worker.run(context());

        assertEquals("COMPLETED", result.status());
        assertEquals(new Counters(0, 0, 0, 0), lastCounters());
        verify(ingestionService, never()).ingestForJob(any(), any());
    }
}
