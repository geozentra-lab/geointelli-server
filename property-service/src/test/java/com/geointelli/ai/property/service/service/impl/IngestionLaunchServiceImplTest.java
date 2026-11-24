package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
import com.geointelli.ai.property.service.service.ReferenceDataScopeService;

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
    private ReferenceDataScopeService scopes;

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
        scopes = mock(ReferenceDataScopeService.class);
        when(scopes.countyFips(1L)).thenReturn(Optional.of("12003"));
        when(scopes.stateFips("FL")).thenReturn(Optional.of("12"));
        service = new IngestionLaunchServiceImpl(jobService, store, workerService, new IngestionJobMapper(jobService, store),
            countyService, properties, new ObjectMapper(), scopes);
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
        @DisplayName("SCRUM-201: when a reference job is retried, then the same stored file and vintage are reloaded from the start")
        void whenReferenceJobRetried_thenReloadedFromStart() {
            IngestionJob failed = previous(ImportJobType.REFERENCE_DATA_IMPORT, IngestionDataset.FLOOD_ZONES, "FAILED");
            failed.setInputStorage("S3");
            failed.setInputLocation("ingestion/inputs/old");
            failed.setParametersJson("{\"inputs\":[{\"name\":\"12003_baker.csv\",\"size\":10,\"etag\":\"e1\"}],"
                + "\"downloadedOn\":\"2026-11-14\"}");
            when(store.describe("ingestion/inputs/old", "12003_baker.csv")).thenReturn(Optional.of(new StoredInput(10, "e1")));

            service.retry(failed.getId(), "admin-1");

            IngestionJob job = createdJob();
            assertEquals(ImportJobType.REFERENCE_DATA_IMPORT, job.getJobType());
            assertEquals("ingestion/inputs/old", job.getInputLocation());
            assertEquals(failed.getParametersJson(), job.getParametersJson());
            assertNull(job.getCheckpointJson(), "a load replaces its county, so it starts again");
            assertEquals(0, job.getSucceeded());
            assertEquals(2, job.getAttempt());
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

    @Nested
    @DisplayName("Starting environmental reference loads (SCRUM-201)")
    class StartReference {
        private static final String FLOOD_HEADER = "fips5,DFIRM_ID,FLD_AR_ID,FLD_ZONE,ZONE_SUBTY,SFHA_TF,STATIC_BFE,DEPTH,"
            + "V_DATUM,LEN_UNIT,VELOCITY,VEL_UNIT,DUAL_ZONE,STUDY_TYP,VERSION_ID,SOURCE_CIT,geom_wkt\n";
        private static final String TRACT_HEADER = "fips5,tract_fips,name,land_area_m2,water_area_m2,geom_wkt\n";
        private static final LocalDate DOWNLOADED = LocalDate.of(2026, 11, 14);

        private InputSource staged(String name, String content) throws Exception {
            Files.createDirectories(staging.resolve("fema_flood_zones"));
            Files.writeString(staging.resolve(name), content);
            return service.staged(name, Path.of(name).getFileName().toString());
        }

        @Test
        @DisplayName("When a county flood file is valid, then it is stored under its own name with the download date")
        void whenFloodFileValid_thenQueuedWithVintage() throws Exception {
            InputSource input = staged("fema_flood_zones/12003_baker.csv",
                FLOOD_HEADER + "12003,12003C,A1,AE,,T,7,-9999,,,-9999,,,NP,1,x,\"POLYGON((0 0,1 0,1 1,0 0))\"\n");

            service.startReference(IngestionDataset.FLOOD_ZONES, "Baker", "FL", input, DOWNLOADED, "admin-1");

            IngestionJob job = createdJob();
            assertEquals(ImportJobType.REFERENCE_DATA_IMPORT, job.getJobType());
            assertEquals(IngestionDataset.FLOOD_ZONES, job.getDataset());
            assertSame(county, job.getCounty());
            assertTrue(job.getParametersJson().contains("\"name\":\"12003_baker.csv\""));
            assertTrue(job.getParametersJson().contains("\"downloadedOn\":\"2026-11-14\""));
            verify(store).store(any(), eq("12003_baker.csv"), any(), anyLong());
            verify(workerService).poll();
        }

        @Test
        @DisplayName("When the first row belongs to another county, then it is rejected before anything is stored")
        void whenFileOfAnotherCounty_thenRejected() throws Exception {
            InputSource input = staged("fema_flood_zones/12086_miami-dade.csv",
                FLOOD_HEADER + "12086,12086C,A1,AE,,T,7,-9999,,,-9999,,,NP,1,x,\"POLYGON((0 0,1 0,1 1,0 0))\"\n");

            var error = assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.FLOOD_ZONES, "Baker", "FL", input, DOWNLOADED, null));
            assertTrue(error.getMessage().contains("12086"));
            verify(store, never()).store(any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("When county, downloadedOn or a column is wrong for the dataset, then the request is rejected")
        void whenRequestInvalid_thenRejected() throws Exception {
            InputSource flood = staged("fema_flood_zones/12003_baker.csv",
                FLOOD_HEADER + "12003,12003C,A1,AE,,T,7,-9999,,,-9999,,,NP,1,x,\"POLYGON((0 0,1 0,1 1,0 0))\"\n");
            InputSource tracts = staged("tracts.csv", TRACT_HEADER + "12003,12003000100,T1,1,0,\"POLYGON((0 0,1 0,1 1,0 0))\"\n");
            InputSource noColumn = staged("broken.csv", "fips5,tract_fips\n12003,12003000100\n");

            assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.FLOOD_ZONES, null, "FL", flood, DOWNLOADED, null));
            assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.CENSUS_TRACTS, "Baker", "FL", tracts, DOWNLOADED, null));
            assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.CENSUS_TRACTS, null, "FL", tracts, null, null));
            assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.CENSUS_TRACTS, null, "FL", tracts, LocalDate.now().plusDays(1), null));
            var missing = assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.CENSUS_TRACTS, null, "FL", noColumn, DOWNLOADED, null));
            assertTrue(missing.getMessage().contains("geom_wkt"));
            assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.ADDRESSES, null, "FL", tracts, DOWNLOADED, null));
            verify(jobService, never()).create(any());
        }

        @Test
        @DisplayName("When a statewide dataset is already loading, or migration V42 is missing, then the request is refused with 409")
        void whenConflict_thenRefused() throws Exception {
            InputSource tracts = staged("tracts.csv", TRACT_HEADER + "12003,12003000100,T1,1,0,\"POLYGON((0 0,1 0,1 1,0 0))\"\n");
            when(jobService.hasActive(IngestionDataset.CENSUS_TRACTS)).thenReturn(true);
            var active = assertThrows(ResponseStatusException.class,
                () -> service.startReference(IngestionDataset.CENSUS_TRACTS, null, "FL", tracts, DOWNLOADED, null));
            assertEquals(HttpStatus.CONFLICT, active.getStatusCode());

            when(scopes.stateFips("FL")).thenThrow(new IllegalStateException("counties.fips_code is missing: apply migration V42 first"));
            var migration = assertThrows(ResponseStatusException.class,
                () -> service.startReference(IngestionDataset.NRI_TRACTS, null, "FL", tracts, DOWNLOADED, null));
            assertEquals(HttpStatus.CONFLICT, migration.getStatusCode());
            verify(jobService, never()).create(any());
        }
    }

    @Nested
    @DisplayName("Starting school reference loads (SCRUM-202)")
    class StartSchoolReference {
        private static final LocalDate BUILT = LocalDate.of(2026, 11, 22);
        private static final String SCHOOL_ROW = "13-0041,13-0041,120039000412,12086,Miami-Dade,CORAL WAY K-8,PUBLIC,"
            + "ELEMENTARY,REGULAR,-1,8,false,true,1215,64.5,1950 SW 13TH AVE,MIAMI,33145,25.75,-80.21,false,\n";
        private static final String GRADE_ROW = "13-0041,13,41,CORAL WAY K-8,2026,A,false,Combination,false,true,No,"
            + "71,68,60,55,72,66,59,61,74,,,,689,10,69,99,58\n";

        private InputSource staged(String name, String content) throws Exception {
            Files.createDirectories(staging.resolve("schools"));
            Files.writeString(staging.resolve(name), content);
            return service.staged(name, Path.of(name).getFileName().toString());
        }

        private InputSource schools(String row) throws Exception {
            return staged("schools/schools.csv", SchoolReferenceDataRowsTest.SCHOOLS_HEADER + "\n" + row);
        }

        @Test
        @DisplayName("When the statewide schools file is valid, then it is queued without a county, with its build date")
        void whenSchoolsValid_thenQueued() throws Exception {
            when(scopes.hasTable("schools")).thenReturn(true);

            service.startReference(IngestionDataset.SCHOOLS, null, "FL", schools(SCHOOL_ROW), BUILT, "admin-1");

            IngestionJob job = createdJob();
            assertEquals(ImportJobType.REFERENCE_DATA_IMPORT, job.getJobType());
            assertEquals(IngestionDataset.SCHOOLS, job.getDataset());
            assertNull(job.getCounty(), "the file holds several counties; each one in it is replaced");
            assertTrue(job.getParametersJson().contains("\"name\":\"schools.csv\""));
            assertTrue(job.getParametersJson().contains("\"downloadedOn\":\"2026-11-22\""));
            verify(workerService).poll();
        }

        @Test
        @DisplayName("When migration V44 is missing, then the request is refused with 409 before anything is stored")
        void whenV44Missing_thenConflict() throws Exception {
            when(scopes.hasTable("schools")).thenReturn(false);

            var missing = assertThrows(ResponseStatusException.class,
                () -> service.startReference(IngestionDataset.SCHOOLS, null, "FL", schools(SCHOOL_ROW), BUILT, null));

            assertEquals(HttpStatus.CONFLICT, missing.getStatusCode());
            assertTrue(missing.getReason().contains("V44"));
            verify(jobService, never()).create(any());
        }

        @Test
        @DisplayName("When a county is given, the first row is in another state, a column is missing or the grades are not "
            + "Florida's, then the request is rejected")
        void whenWrongInput_thenRejected() throws Exception {
            when(scopes.hasTable(any())).thenReturn(true);
            when(scopes.stateFips("GA")).thenReturn(Optional.of("13"));
            InputSource valid = schools(SCHOOL_ROW);
            InputSource georgia = staged("schools/georgia.csv", SchoolReferenceDataRowsTest.SCHOOLS_HEADER + "\n"
                + SCHOOL_ROW.replace(",12086,", ",13121,"));
            InputSource noColumn = staged("schools/zones.csv", "fips5,level\n12086,ELEMENTARY\n");
            InputSource grades = staged("schools/school_grades.csv", SchoolReferenceDataRowsTest.GRADES_HEADER + "\n" + GRADE_ROW);
            InputSource notFldoe = staged("schools/other.csv", SchoolReferenceDataRowsTest.GRADES_HEADER + "\n"
                + GRADE_ROW.replace("13-0041,", "X-1,"));

            assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.SCHOOLS, "Miami-Dade", "FL", valid, BUILT, null));
            assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.SCHOOLS, null, "FL", georgia, BUILT, null));
            assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.SCHOOL_ZONES, null, "FL", noColumn, BUILT, null));
            assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.SCHOOL_GRADES, null, "GA", grades, BUILT, null));
            assertThrows(IllegalArgumentException.class,
                () -> service.startReference(IngestionDataset.SCHOOL_GRADES, null, "FL", notFldoe, BUILT, null));
            verify(jobService, never()).create(any());

            service.startReference(IngestionDataset.SCHOOL_GRADES, null, "FL", grades, BUILT, null);
            assertEquals(IngestionDataset.SCHOOL_GRADES, createdJob().getDataset());
        }
    }
}
