package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.config.IngestionProperties;
import com.geointelli.ai.property.service.dto.ImageSaveRequest;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImage;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.exception.ImageDownloadException;
import com.geointelli.ai.property.service.service.CsvImportService;
import com.geointelli.ai.property.service.service.IngestionInputStore;
import com.geointelli.ai.property.service.service.IngestionJobContext;
import com.geointelli.ai.property.service.service.IngestionJobService;
import com.geointelli.ai.property.service.service.IngestionJobService.Counters;
import com.geointelli.ai.property.service.service.IngestionJobWorker.Result;
import com.geointelli.ai.property.service.service.IngestionLeaseLostException;
import com.geointelli.ai.property.service.service.PropertyImageService;
import jakarta.persistence.EntityManager;

/** Drives the CSV worker with files in a temporary folder and a mocked transaction manager. */
class CsvIngestionJobWorkerTest {
    private static final String ADDRESS_HEADER = "property_id,address,building_number,city,house_number_suffix,message,"
        + "street_name,street_number,street_prefix,street_suffix,street_suffix_direction,unit,zip\n";

    @TempDir Path directory;
    private CsvImportService csvImportService;
    private PropertyImageService propertyImageService;
    private IngestionJobService jobService;
    private PlatformTransactionManager transactionManager;
    private CsvIngestionJobWorker worker;
    private IngestionJob job;
    private final List<Long> importedRows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        csvImportService = mock(CsvImportService.class);
        propertyImageService = mock(PropertyImageService.class);
        jobService = mock(IngestionJobService.class);
        when(jobService.recordProgress(any(), anyString(), any(), any(), any(), any())).thenReturn(1);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenAnswer(invocation -> mock(TransactionStatus.class));
        IngestionProperties properties = new IngestionProperties();
        properties.getJobs().setCsvBatchSize(2);
        worker = new CsvIngestionJobWorker(csvImportService, propertyImageService, mock(EntityManager.class),
            new ObjectMapper(), properties);
        job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setJobType(ImportJobType.CSV_IMPORT);
        job.setDataset(IngestionDataset.ADDRESSES);
        job.setSourceCounty("Baker");
        job.setSourceState("FL");
        job.setInputStorage("LOCAL");
        job.setInputLocation("inputs");
        job.setStatus("RUNNING");
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

    private void addresses(int rows) throws IOException {
        StringBuilder csv = new StringBuilder(ADDRESS_HEADER);
        for (int row = 1; row <= rows; row++) csv.append("12,").append(row).append(" Main St,,Miami,,,Main,").append(row).append(",,St,,,33101\n");
        Files.writeString(directory.resolve("addresses.csv"), csv.toString());
    }

    private Consumer<CSVRecord> recordingImporter(long failingRow) {
        return record -> {
            if (record.getRecordNumber() == failingRow) throw new IllegalArgumentException("bad zip");
            importedRows.add(record.getRecordNumber());
        };
    }

    private List<Counters> recordedCounters() {
        ArgumentCaptor<Counters> counters = ArgumentCaptor.forClass(Counters.class);
        verify(jobService, atLeastOnce()).recordProgress(eq(job.getId()), eq("worker-1"), counters.capture(), any(), any(), any());
        return counters.getAllValues();
    }

    private List<String> recordedCheckpoints() {
        ArgumentCaptor<String> checkpoints = ArgumentCaptor.forClass(String.class);
        verify(jobService, atLeastOnce()).recordProgress(eq(job.getId()), eq("worker-1"), any(), any(), checkpoints.capture(), any());
        return checkpoints.getAllValues();
    }

    @Test
    @DisplayName("When a related file is imported, then rows commit in batches and each batch records its checkpoint")
    void whenImportingRelatedFile_thenBatchesCommitWithCheckpoints() throws Exception {
        addresses(5);
        when(csvImportService.relatedRowImporter(IngestionDataset.ADDRESSES, "Baker", "FL")).thenReturn(recordingImporter(-1));

        Result result = worker.run(context());

        assertEquals("COMPLETED", result.status());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), importedRows);
        // count of the file, three batches (2 + 2 + 1), then the final write
        verify(transactionManager, times(5)).commit(any());
        verify(transactionManager, never()).rollback(any());
        List<Counters> counters = recordedCounters();
        assertEquals(new Counters(5, 0, 0, 0), counters.get(0));
        assertEquals(new Counters(5, 2, 0, 0), counters.get(1));
        assertEquals(new Counters(5, 5, 0, 0), counters.get(4));
        assertTrue(recordedCheckpoints().get(1).contains("\"committedRows\":2"));
        assertTrue(recordedCheckpoints().get(3).contains("\"committedRows\":5"));
    }

    @Test
    @DisplayName("When a job resumes from a checkpoint, then the committed rows are skipped and the total is not recounted")
    void whenResuming_thenCommittedRowsAreSkipped() throws Exception {
        addresses(5);
        job.setCheckpointJson("{\"stepIndex\":0,\"phase\":\"ROWS\",\"committedRows\":3,\"countedSteps\":[0]}");
        job.setTotal(5);
        job.setSucceeded(3);
        job.setProcessed(3);
        when(csvImportService.relatedRowImporter(IngestionDataset.ADDRESSES, "Baker", "FL")).thenReturn(recordingImporter(-1));

        Result result = worker.run(context());

        assertEquals("COMPLETED", result.status());
        assertEquals(List.of(4L, 5L), importedRows);
        List<Counters> counters = recordedCounters();
        assertEquals(new Counters(5, 5, 0, 0), counters.get(counters.size() - 1));
    }

    @Test
    @DisplayName("When a row is invalid, then its batch rolls back, earlier batches stay, the row is recorded and the job fails there")
    void whenRowIsInvalid_thenBatchRollsBackAndJobFails() throws Exception {
        addresses(5);
        when(csvImportService.relatedRowImporter(IngestionDataset.ADDRESSES, "Baker", "FL")).thenReturn(recordingImporter(4));

        Result result = worker.run(context());

        assertEquals("FAILED", result.status());
        assertEquals("INVALID_ROW", result.errorCode());
        assertTrue(result.errorMessage().startsWith("addresses.csv:4: "));
        assertEquals(List.of(1L, 2L, 3L), importedRows);
        verify(transactionManager, times(1)).rollback(any());
        // count, first batch, then the failure record
        verify(transactionManager, times(3)).commit(any());
        verify(jobService).addItem(job.getId(), "addresses.csv:4", "FAILED", "INVALID_ROW", "bad zip");
        List<Counters> counters = recordedCounters();
        assertEquals(new Counters(5, 2, 0, 1), counters.get(counters.size() - 1));
        assertTrue(recordedCheckpoints().get(recordedCheckpoints().size() - 1).contains("\"committedRows\":2"));
    }

    @Test
    @DisplayName("When the lease is lost, then the batch rolls back and the worker stops without committing")
    void whenLeaseIsLost_thenBatchRollsBack() throws Exception {
        addresses(4);
        when(csvImportService.relatedRowImporter(IngestionDataset.ADDRESSES, "Baker", "FL")).thenReturn(recordingImporter(-1));
        when(jobService.recordProgress(any(), anyString(), any(), any(), any(), any())).thenReturn(1, 1, 0);

        assertThrows(IngestionLeaseLostException.class, () -> worker.run(context()));

        verify(transactionManager, times(2)).commit(any());
        verify(transactionManager, times(1)).rollback(any());
        assertEquals(List.of(1L, 2L, 3L, 4L), importedRows);
    }

    @Test
    @DisplayName("When importing a folder, then files run in county order and owners load in one transaction")
    void whenImportingFolder_thenFilesRunInOrder() throws Exception {
        job.setDataset(IngestionDataset.FOLDER);
        job.setParametersJson("{\"inputs\":[{\"name\":\"owners.csv\",\"size\":1,\"etag\":\"a\"},"
            + "{\"name\":\"addresses.csv\",\"size\":1,\"etag\":\"b\"},{\"name\":\"properties.csv\",\"size\":1,\"etag\":\"c\"},"
            + "{\"name\":\"property_owner.csv\",\"size\":1,\"etag\":\"d\"}]}");
        Files.writeString(directory.resolve("properties.csv"), "id,folio\n1,F1\n2,F2\n");
        addresses(1);
        Files.writeString(directory.resolve("owners.csv"), "id,name\n1,Ann\n");
        Files.writeString(directory.resolve("property_owner.csv"), "property_id,owner_id\n1,1\n1,1\n");
        List<String> order = new ArrayList<>();
        when(csvImportService.propertyRowImporter("Baker", "FL")).thenReturn(record -> order.add("property " + record.getRecordNumber()));
        when(csvImportService.hasParentLinks(any())).thenReturn(false);
        when(csvImportService.relatedRowImporter(IngestionDataset.ADDRESSES, "Baker", "FL")).thenReturn(record -> order.add("address"));
        doAnswer(invocation -> order.add("owners")).when(csvImportService)
            .importOwners(directory.resolve("owners.csv"), directory.resolve("property_owner.csv"), "Baker", "FL");

        Result result = worker.run(context());

        assertEquals("COMPLETED", result.status());
        assertEquals(List.of("property 1", "property 2", "address", "owners"), order);
        verify(csvImportService, never()).parentLinkImporter(any(), any());
        List<Counters> counters = recordedCounters();
        assertEquals(new Counters(6, 6, 0, 0), counters.get(counters.size() - 1));
    }

    @Test
    @DisplayName("When properties have parent links, then the parent pass runs after the rows without counting them twice")
    void whenPropertiesHaveParents_thenParentPassRuns() throws Exception {
        job.setDataset(IngestionDataset.PROPERTIES);
        Files.writeString(directory.resolve("properties.csv"), "id,folio,parent_property_id\n1,F1,\n2,F2,1\n3,F3,1\n");
        List<String> order = new ArrayList<>();
        when(csvImportService.propertyRowImporter("Baker", "FL")).thenReturn(record -> order.add("row " + record.getRecordNumber()));
        when(csvImportService.hasParentLinks(any())).thenReturn(true);
        when(csvImportService.parentLinkImporter("Baker", "FL")).thenReturn(record -> order.add("parent " + record.getRecordNumber()));

        Result result = worker.run(context());

        assertEquals("COMPLETED", result.status());
        assertEquals(List.of("row 1", "row 2", "row 3", "parent 1", "parent 2", "parent 3"), order);
        List<Counters> counters = recordedCounters();
        assertEquals(new Counters(3, 3, 0, 0), counters.get(counters.size() - 1));
        assertTrue(recordedCheckpoints().stream().anyMatch(checkpoint -> checkpoint.contains("\"phase\":\"PARENTS\"")));
    }

    @Test
    @DisplayName("When importing images, then each image downloads before its transaction and broken images are skipped")
    void whenImportingImages_thenDownloadHappensOutsideTransaction() throws Exception {
        job.setDataset(IngestionDataset.PROPERTY_IMAGES);
        Files.writeString(directory.resolve("property_images.csv"), "property_id,original_url,primary_image,display_order\n"
            + "12,http://img/1.jpg,true,1\n12,http://img/broken.jpg,false,2\n");
        Property property = new Property();
        when(csvImportService.mappedProperty("Baker", "FL", 12L)).thenReturn(property);
        ImageSaveRequest download = new ImageSaveRequest();
        when(propertyImageService.download("http://img/1.jpg")).thenReturn(download);
        when(propertyImageService.download("http://img/broken.jpg")).thenThrow(new ImageDownloadException("404"));
        when(propertyImageService.saveDownloaded(property, download, true, 1)).thenReturn(new PropertyImage());

        Result result = worker.run(context());

        assertEquals("COMPLETED", result.status());
        InOrder inOrder = inOrder(propertyImageService, transactionManager);
        inOrder.verify(propertyImageService).download("http://img/1.jpg");
        inOrder.verify(transactionManager).getTransaction(any());
        inOrder.verify(propertyImageService).saveDownloaded(property, download, true, 1);
        verify(jobService).addItem(eq(job.getId()), eq("property_images.csv:2"), eq("SKIPPED"), eq("BROKEN_IMAGE"), any());
        List<Counters> counters = recordedCounters();
        assertEquals(new Counters(2, 1, 1, 0), counters.get(counters.size() - 1));
    }

    @Test
    @DisplayName("When a message contains a server path, then the stored text replaces it")
    void whenMessageHasPath_thenItIsRemoved() {
        assertEquals("Invalid CSV record 3 in <path>: bad", CsvIngestionJobWorker.sanitize("Invalid CSV record 3 in C:\\data\\in.csv: bad"));
        assertEquals("missing <path>", CsvIngestionJobWorker.sanitize("missing /srv/imports/a.csv"));
    }
}
