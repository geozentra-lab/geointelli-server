package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.config.IngestionProperties;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.mapper.IngestionJobMapper;
import com.geointelli.ai.property.service.service.CountyService;
import com.geointelli.ai.property.service.service.IngestionInputStore;
import com.geointelli.ai.property.service.service.IngestionInputStore.StoredInput;
import com.geointelli.ai.property.service.service.IngestionJobService;
import com.geointelli.ai.property.service.service.IngestionJobWorkerService;
import com.geointelli.ai.property.service.service.IngestionLaunchService.InputSource;

class IngestionLaunchServiceImplTest {
    private static final String ADDRESS_HEADER = "property_id,address,building_number,city,house_number_suffix,message,"
        + "street_name,street_number,street_prefix,street_suffix,street_suffix_direction,unit,zip\n";

    @TempDir Path staging;
    private IngestionJobService jobService;
    private IngestionInputStore store;
    private IngestionJobWorkerService workerService;
    private CountyService countyService;
    private IngestionProperties properties;
    private IngestionLaunchServiceImpl service;
    private County county;

    @BeforeEach
    void setUp() throws Exception {
        jobService = mock(IngestionJobService.class);
        when(jobService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        store = mock(IngestionInputStore.class);
        when(store.storageType()).thenReturn("S3");
        when(store.locationFor(any())).thenAnswer(invocation -> "ingestion/inputs/" + invocation.getArgument(0));
        when(store.store(any(), any(), any(), anyLong())).thenAnswer(invocation ->
            new StoredInput(invocation.getArgument(3), "etag-" + invocation.getArgument(1)));
        workerService = mock(IngestionJobWorkerService.class);
        countyService = mock(CountyService.class);
        county = new County();
        county.setId(1L);
        when(countyService.resolve("Baker", "FL")).thenReturn(county);
        when(countyService.miamiDade()).thenReturn(county);
        properties = new IngestionProperties();
        properties.getInput().setStagingDirectory(staging.toString());
        service = new IngestionLaunchServiceImpl(jobService, store, workerService, new IngestionJobMapper(jobService, store),
            countyService, properties, new ObjectMapper());
    }

    private InputSource addresses(String content) {
        return service.upload(new MockMultipartFile("file", "addresses.csv", "text/csv",
            content.getBytes(StandardCharsets.UTF_8)), "addresses.csv");
    }

    private IngestionJob createdJob() {
        ArgumentCaptor<IngestionJob> job = ArgumentCaptor.forClass(IngestionJob.class);
        verify(jobService).create(job.capture());
        return job.getValue();
    }

    @Nested
    @DisplayName("Starting CSV jobs")
    class StartCsv {

        @Test
        @DisplayName("When the file is valid, then it is stored under the job and the job is queued and polled")
        void whenValid_thenStoredAndQueued() throws Exception {
            ImportJobDTO dto = service.startCsv(IngestionDataset.ADDRESSES, "Baker", "FL",
                List.of(addresses(ADDRESS_HEADER + "12,1 Main St,,Miami,,,Main,1,,St,,,33101\n")), "user-1");

            IngestionJob job = createdJob();
            assertEquals("QUEUED", job.getStatus());
            assertEquals(ImportJobType.CSV_IMPORT, job.getJobType());
            assertEquals(IngestionDataset.ADDRESSES, job.getDataset());
            assertSame(county, job.getCounty());
            assertEquals("S3", job.getInputStorage());
            assertEquals("ingestion/inputs/" + job.getId(), job.getInputLocation());
            assertNull(job.getInputScope());
            assertNull(job.getLeaseExpiresAt());
            assertEquals("user-1", job.getCreatedBy());
            assertTrue(job.getParametersJson().contains("\"name\":\"addresses.csv\""));
            assertTrue(job.getParametersJson().contains("\"etag\":\"etag-addresses.csv\""));
            verify(store).store(eq("ingestion/inputs/" + job.getId()), eq("addresses.csv"), any(), anyLong());
            verify(countyService, never()).ensureExists(any(), any());
            verify(workerService).poll();
            assertEquals(job.getId().toString(), dto.getId());
            assertEquals("QUEUED", dto.getStatus());
            assertEquals(Boolean.FALSE, dto.getRetryable());
            assertEquals(IngestionJobMapper.JOB_NOT_FINISHED, dto.getRetryBlockedReason());
        }

        @Test
        @DisplayName("When a required column is missing, then the request is rejected before anything is stored")
        void whenColumnMissing_thenRejectedBeforeStoring() throws Exception {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.startCsv(
                IngestionDataset.ADDRESSES, "Baker", "FL", List.of(addresses("property_id,address\n12,x\n")), null));

            assertTrue(error.getMessage().contains("addresses.csv is missing the columns"));
            assertTrue(error.getMessage().contains("zip"));
            verify(store, never()).store(any(), any(), any(), anyLong());
            verify(jobService, never()).create(any());
        }

        @Test
        @DisplayName("When county, state or file is missing or empty, then the request is rejected")
        void whenCountyOrFileMissing_thenRejected() {
            InputSource valid = addresses(ADDRESS_HEADER + "12,x,,Miami,,,Main,1,,St,,,33101\n");
            assertThrows(IllegalArgumentException.class, () -> service.startCsv(IngestionDataset.ADDRESSES, " ", "FL", List.of(valid), null));
            assertThrows(IllegalArgumentException.class, () -> service.startCsv(IngestionDataset.ADDRESSES, "Baker", "FL", List.of(), null));
            assertThrows(IllegalArgumentException.class, () -> service.upload(
                new MockMultipartFile("file", "addresses.csv", "text/csv", new byte[0]), "addresses.csv"));
            when(countyService.resolve("Nowhere", "FL")).thenThrow(new IllegalArgumentException("Configure county"));
            assertThrows(IllegalArgumentException.class, () -> service.startCsv(IngestionDataset.ADDRESSES, "Nowhere", "FL", List.of(valid), null));
            verify(jobService, never()).create(any());
        }

        @Test
        @DisplayName("When importing properties, then the county is created if the state is known")
        void whenImportingProperties_thenCountyIsCreated() {
            String header = String.join(",", IngestionDataset.PROPERTIES.getRequiredHeaders()) + "\n";
            InputSource file = service.upload(new MockMultipartFile("file", "properties.csv", "text/csv",
                (header + "1,2,3,,,,,,,,,F1,,,,,,,,,,,,,2000\n").getBytes(StandardCharsets.UTF_8)), "properties.csv");

            service.startCsv(IngestionDataset.PROPERTIES, "Baker", "FL", List.of(file), null);

            verify(countyService).ensureExists("Baker", "FL");
            verify(countyService).resolve("Baker", "FL");
        }

        @Test
        @DisplayName("When a folder lacks a required file, then it is rejected naming the file")
        void whenFolderLacksFile_thenRejected() {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.startCsv(
                IngestionDataset.FOLDER, "Baker", "FL", List.of(addresses(ADDRESS_HEADER + "12,x,,Miami,,,Main,1,,St,,,33101\n")), null));
            assertEquals("Missing file properties.csv", error.getMessage());
        }

        @Test
        @DisplayName("When inputs are local, then the job is pinned to this instance with a queue deadline")
        void whenInputsAreLocal_thenJobIsPinned() {
            when(store.storageType()).thenReturn("LOCAL");
            when(store.scope()).thenReturn("host-a");

            service.startCsv(IngestionDataset.ADDRESSES, "Baker", "FL",
                List.of(addresses(ADDRESS_HEADER + "12,x,,Miami,,,Main,1,,St,,,33101\n")), null);

            IngestionJob job = createdJob();
            assertEquals("LOCAL", job.getInputStorage());
            assertEquals("host-a", job.getInputScope());
            assertNotNull(job.getLeaseExpiresAt());
        }
    }

    @Nested
    @DisplayName("Staged inputs")
    class Staged {

        @Test
        @DisplayName("When a staged name leaves the staging directory or staging is disabled, then it is rejected")
        void whenStagedNameEscapes_thenRejected() throws Exception {
            Files.writeString(staging.resolve("in.csv"), ADDRESS_HEADER);
            assertEquals(ADDRESS_HEADER.length(), service.staged("in.csv", "addresses.csv").size());
            assertThrows(IllegalArgumentException.class, () -> service.staged("../in.csv", "addresses.csv"));
            assertThrows(IllegalArgumentException.class, () -> service.staged(staging.toAbsolutePath().toString(), "addresses.csv"));
            assertThrows(IllegalArgumentException.class, () -> service.staged("missing.csv", "addresses.csv"));
            properties.getInput().setStagingDirectory("");
            assertThrows(IllegalArgumentException.class, () -> service.staged("in.csv", "addresses.csv"));
        }

        @Test
        @DisplayName("When a staged folder is named, then only the known county files are taken")
        void whenStagedFolder_thenKnownFilesAreTaken() throws Exception {
            Files.createDirectories(staging.resolve("baker"));
            Files.writeString(staging.resolve("baker/addresses.csv"), ADDRESS_HEADER);
            Files.writeString(staging.resolve("baker/notes.txt"), "ignored");

            List<InputSource> inputs = service.stagedFolder("baker");

            assertEquals(List.of("addresses.csv"), inputs.stream().map(InputSource::name).toList());
            assertThrows(IllegalArgumentException.class, () -> service.uploadedFolder(List.of(
                new MockMultipartFile("files", "notes.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8)))));
        }
    }

    @Nested
    @DisplayName("API jobs and retries")
    class ApiAndRetry {

        private IngestionJob previous(ImportJobType type, IngestionDataset dataset, String status) {
            IngestionJob job = new IngestionJob();
            job.setId(UUID.randomUUID());
            job.setJobType(type);
            job.setDataset(dataset);
            job.setStatus(status);
            job.setSourceCounty("Baker");
            job.setSourceState("FL");
            job.setCounty(county);
            job.setInputStorage(type == ImportJobType.CSV_IMPORT ? "S3" : "NONE");
            job.setInputLocation(type == ImportJobType.CSV_IMPORT ? "ingestion/inputs/old" : null);
            job.setParametersJson("{\"inputs\":[{\"name\":\"addresses.csv\",\"size\":10,\"etag\":\"e1\"}]}");
            job.setCheckpointJson("{\"stepIndex\":0,\"phase\":\"ROWS\",\"committedRows\":4,\"countedSteps\":[0]}");
            job.setCurrentStep("addresses.csv");
            job.setTotal(10);
            job.setSucceeded(4);
            job.setSkipped(0);
            job.setFailed(1);
            job.setProcessed(5);
            job.setAttempt(1);
            when(jobService.find(job.getId())).thenReturn(Optional.of(job));
            return job;
        }

        @Test
        @DisplayName("When an API dataset already has an active job, then starting another is refused with 409")
        void whenApiDatasetActive_thenRefused() {
            when(jobService.hasActive(IngestionDataset.API_SALES)).thenReturn(true);

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.startApi(IngestionDataset.API_SALES, null));

            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
            assertTrue(error.getReason().startsWith(IngestionLaunchServiceImpl.DATASET_JOB_ACTIVE));
            verify(jobService, never()).create(any());
        }

        @Test
        @DisplayName("When an API job is started, then it is queued for Miami-Dade without inputs")
        void whenApiJobStarted_thenQueued() {
            ImportJobDTO dto = service.startApi(IngestionDataset.API_ADDRESSES, "admin");

            IngestionJob job = createdJob();
            assertEquals(ImportJobType.PROPERTY_INGESTION, job.getJobType());
            assertEquals("NONE", job.getInputStorage());
            assertEquals("Miami-Dade", job.getSourceCounty());
            assertEquals(IngestionDataset.API_ADDRESSES, dto.getDataset());
            verify(workerService).poll();
        }

        @Test
        @DisplayName("When a failed CSV job is retried, then a new job resumes from its checkpoint on the same inputs")
        void whenCsvJobRetried_thenResumesFromCheckpoint() {
            IngestionJob failed = previous(ImportJobType.CSV_IMPORT, IngestionDataset.ADDRESSES, "FAILED");
            when(store.describe("ingestion/inputs/old", "addresses.csv")).thenReturn(Optional.of(new StoredInput(10, "e1")));

            ImportJobDTO dto = service.retry(failed.getId(), "admin");

            IngestionJob job = createdJob();
            assertNotEquals(failed.getId(), job.getId());
            assertEquals("QUEUED", job.getStatus());
            assertEquals(failed.getId(), job.getRetryOfJobId());
            assertEquals(2, job.getAttempt());
            assertEquals("ingestion/inputs/old", job.getInputLocation());
            assertEquals(failed.getCheckpointJson(), job.getCheckpointJson());
            assertEquals(10, job.getTotal());
            assertEquals(4, job.getSucceeded());
            assertEquals(0, job.getFailed());
            assertEquals(4, job.getProcessed());
            assertEquals(failed.getId().toString(), dto.getRetryOfJobId());
            verify(workerService).poll();
        }

        @Test
        @DisplayName("When the stored input changed or disappeared, then the retry is refused")
        void whenInputChangedOrMissing_thenRefused() {
            IngestionJob failed = previous(ImportJobType.CSV_IMPORT, IngestionDataset.ADDRESSES, "FAILED");
            when(store.describe("ingestion/inputs/old", "addresses.csv")).thenReturn(Optional.of(new StoredInput(10, "other")));
            ResponseStatusException changed = assertThrows(ResponseStatusException.class, () -> service.retry(failed.getId(), null));
            assertTrue(changed.getReason().startsWith(IngestionLaunchServiceImpl.INPUT_CHANGED));

            when(store.describe("ingestion/inputs/old", "addresses.csv")).thenReturn(Optional.empty());
            ResponseStatusException missing = assertThrows(ResponseStatusException.class, () -> service.retry(failed.getId(), null));
            assertTrue(missing.getReason().startsWith(IngestionLaunchServiceImpl.INPUT_UNAVAILABLE));
            verify(jobService, never()).create(any());
        }

        @Test
        @DisplayName("When the job is active, completed, already retried or on another instance, then the retry is refused with the reason")
        void whenNotEligible_thenRefusedWithReason() {
            IngestionJob running = previous(ImportJobType.CSV_IMPORT, IngestionDataset.ADDRESSES, "RUNNING");
            assertReason(running, IngestionJobMapper.JOB_NOT_FINISHED);

            IngestionJob completed = previous(ImportJobType.CSV_IMPORT, IngestionDataset.ADDRESSES, "COMPLETED");
            assertReason(completed, IngestionJobMapper.JOB_COMPLETED);

            IngestionJob retried = previous(ImportJobType.CSV_IMPORT, IngestionDataset.ADDRESSES, "INTERRUPTED");
            when(jobService.hasActiveRetry(retried.getId())).thenReturn(true);
            assertReason(retried, IngestionJobMapper.RETRY_ALREADY_ACTIVE);

            IngestionJob elsewhere = previous(ImportJobType.CSV_IMPORT, IngestionDataset.ADDRESSES, "FAILED");
            elsewhere.setInputStorage("LOCAL");
            elsewhere.setInputScope("host-b");
            when(store.scope()).thenReturn("host-a");
            assertReason(elsewhere, IngestionJobMapper.INPUT_ON_OTHER_INSTANCE);

            assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.retry(UUID.randomUUID(), null)).getStatusCode());
            verify(jobService, never()).create(any());
        }

        private void assertReason(IngestionJob job, String reason) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.retry(job.getId(), null));
            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
            assertTrue(error.getReason().startsWith(reason + ": "), error.getReason());
        }

        @Test
        @DisplayName("When an API job is retried, then counters restart and another active job for the dataset blocks it")
        void whenApiJobRetried_thenCountersRestart() {
            IngestionJob partial = previous(ImportJobType.PROPERTY_INGESTION, IngestionDataset.API_SALES, "PARTIAL");

            service.retry(partial.getId(), null);
            IngestionJob job = createdJob();
            assertEquals(0, job.getTotal());
            assertEquals(0, job.getSucceeded());
            assertNull(job.getCheckpointJson());
            assertEquals(partial.getId(), job.getRetryOfJobId());

            when(jobService.hasActive(IngestionDataset.API_SALES)).thenReturn(true);
            ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.retry(partial.getId(), null));
            assertTrue(error.getReason().startsWith(IngestionLaunchServiceImpl.DATASET_JOB_ACTIVE));
        }

        @Test
        @DisplayName("When a job is read, then its failed and skipped records are included")
        void whenJobRead_thenItemsIncluded() {
            IngestionJob failed = previous(ImportJobType.CSV_IMPORT, IngestionDataset.ADDRESSES, "FAILED");
            when(jobService.items(failed.getId(), IngestionLaunchServiceImpl.ITEM_LIMIT)).thenReturn(List.of(
                new com.geointelli.ai.property.service.dto.IngestionJobItemDTO("addresses.csv:5", "FAILED", "INVALID_ROW", "bad")));

            ImportJobDTO dto = service.get(failed.getId());

            assertEquals(1, dto.getItems().size());
            assertEquals("addresses.csv:5", dto.getItems().get(0).getItemKey());
            assertEquals(Boolean.TRUE, dto.getRetryable());
            assertEquals(4, dto.getCompleted());
            assertEquals(1, dto.getFailed());
        }
    }
}
