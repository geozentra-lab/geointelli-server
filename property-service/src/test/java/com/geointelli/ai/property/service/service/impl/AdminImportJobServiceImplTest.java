package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.ImageBackfillJobDTO;
import com.geointelli.ai.property.service.dto.ImageBackfillResultDTO;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobPageDTO;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.mapper.IngestionJobMapper;
import com.geointelli.ai.property.service.repository.IngestionJobRepository;
import com.geointelli.ai.property.service.repository.IngestionJobRepository.ImportJobRow;
import com.geointelli.ai.property.service.service.ImageBackfillClaimService;
import com.geointelli.ai.property.service.service.IngestionInputStore;
import com.geointelli.ai.property.service.service.IngestionJobService;
import com.geointelli.ai.property.service.service.IngestionLaunchService;
import com.geointelli.ai.property.service.service.PropertyImageBackfillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AdminImportJobServiceImplTest {

    ImageBackfillClaimService imageBackfillClaimService = mock(ImageBackfillClaimService.class);
    PropertyImageBackfillService propertyImageBackfillService = mock(PropertyImageBackfillService.class);
    IngestionJobService ingestionJobService = mock(IngestionJobService.class);
    IngestionJobRepository ingestionJobs = mock(IngestionJobRepository.class);
    IngestionInputStore inputStore = mock(IngestionInputStore.class);
    IngestionLaunchService ingestionLaunchService = mock(IngestionLaunchService.class);

    AdminImportJobServiceImpl adminImportJobService = new AdminImportJobServiceImpl(imageBackfillClaimService,
            propertyImageBackfillService, ingestionJobService, ingestionJobs,
            new IngestionJobMapper(ingestionJobService, inputStore), ingestionLaunchService);

    private static final Instant CREATED_AT = Instant.parse("2026-11-05T10:00:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-11-05T10:05:00Z");
    private static final UUID INGESTION_ID = UUID.fromString("4f1c7d2e-9b2a-4c1e-8f3a-2d9e6b7c1a55");

    private static ImageBackfillJobDTO backfillJob(long id) {
        ImageBackfillJobDTO job = new ImageBackfillJobDTO();
        job.setJobId(id);
        job.setCountyId(1L);
        job.setStatus("PARTIAL");
        job.setBatchSize(3);
        job.setTotal(3);
        job.setProcessed(3);
        job.setCompleted(1);
        job.setSkipped(1);
        job.setFailed(1);
        job.setCreatedAt(CREATED_AT);
        job.setFinishedAt(FINISHED_AT);
        job.setResults(List.of(
                new ImageBackfillResultDTO(10L, "COMPLETED", null, 100L),
                new ImageBackfillResultDTO(11L, "SKIPPED", "NO_SUITABLE_IMAGE_OR_PARCEL", null),
                new ImageBackfillResultDTO(12L, "FAILED", "IMAGE_PROCESSING_OR_STORAGE_FAILED", null)));
        return job;
    }

    private static ImportJobRow row(String id, String type, String dataset, String status, Object createdAt,
                                    String inputStorage, String inputScope, boolean hasActiveRetry) {
        return new ImportJobRow(id, type, dataset, 1L, status, 3, 3, 1, 1, 1, null, null, null, null, null,
                inputStorage, inputScope, type.equals("MAPILLARY_IMAGE_BACKFILL") ? null : 1, null, hasActiveRetry,
                createdAt, null, FINISHED_AT);
    }

    /** The repository returns raw columns; the service rebuilds rows from them in the same order. */
    private static Object[] columns(ImportJobRow row) {
        return new Object[] {row.id(), row.jobType(), row.dataset(), row.countyId(), row.status(), row.total(),
                row.processed(), row.succeeded(), row.skipped(), row.failed(), row.errorCode(), row.errorMessage(),
                row.currentStep(), row.sourceCounty(), row.sourceState(), row.inputStorage(), row.inputScope(),
                row.attempt(), row.retryOfJobId(), row.hasActiveRetry(), row.createdAt(), row.startedAt(), row.finishedAt()};
    }

    private void givenRows(ImportJobRow... rows) {
        when(ingestionJobs.listAllJobs(any(), any(), anyInt(), anyLong()))
                .thenReturn(java.util.Arrays.stream(rows).map(AdminImportJobServiceImplTest::columns).toList());
        when(ingestionJobs.countAllJobs(any(), any())).thenReturn((long) rows.length);
    }

    @Nested
    @DisplayName("ListJobs Tests")
    class ListJobs {

        @Test
        @DisplayName("When backfill and ingestion jobs exist, then both are returned with their type, counters and retry availability")
        void whenJobsExist_thenReturnBothKinds() {
            // Arrange
            givenRows(row(INGESTION_ID.toString(), "CSV_IMPORT", "ADDRESSES", "FAILED", Timestamp.from(CREATED_AT), "S3", null, false),
                    row("7", "MAPILLARY_IMAGE_BACKFILL", null, "PARTIAL", OffsetDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC), null, null, false));

            // Act
            ImportJobPageDTO page = adminImportJobService.listJobs(0, 20, null, null);

            // Assert
            assertEquals(2, page.getJobs().content().size());
            ImportJobDTO ingestion = page.getJobs().content().get(0);
            assertEquals(ImportJobType.CSV_IMPORT, ingestion.getJobType());
            assertEquals(INGESTION_ID.toString(), ingestion.getId());
            assertNull(ingestion.getJobId());
            assertEquals(IngestionDataset.ADDRESSES, ingestion.getDataset());
            assertEquals("FAILED", ingestion.getStatus());
            assertEquals(1, ingestion.getCompleted());
            assertEquals(CREATED_AT, ingestion.getCreatedAt());
            assertEquals(Boolean.TRUE, ingestion.getRetryable());
            assertNull(ingestion.getItems());

            ImportJobDTO backfill = page.getJobs().content().get(1);
            assertEquals(ImportJobType.MAPILLARY_IMAGE_BACKFILL, backfill.getJobType());
            assertEquals("7", backfill.getId());
            assertEquals(7L, backfill.getJobId());
            assertEquals(CREATED_AT, backfill.getCreatedAt());
            assertEquals(Boolean.FALSE, backfill.getRetryable());
            assertEquals(IngestionJobMapper.RETRY_NOT_SUPPORTED, backfill.getRetryBlockedReason());
            assertEquals(2, page.getJobs().totalElements());
            assertEquals(1, page.getJobs().totalPages());
            assertTrue(page.getJobs().first());
            assertTrue(page.getJobs().last());
        }

        @Test
        @DisplayName("When listing, then expired jobs of both kinds are interrupted first and filters reach the query")
        void whenListing_thenInterruptExpiredAndPassFilters() {
            // Arrange
            givenRows();

            // Act
            adminImportJobService.listJobs(2, 5, ImportJobType.CSV_IMPORT, "RUNNING");

            // Assert
            InOrder inOrder = inOrder(imageBackfillClaimService, ingestionJobService, ingestionJobs);
            inOrder.verify(imageBackfillClaimService).interruptExpired();
            inOrder.verify(ingestionJobService).interruptExpired();
            inOrder.verify(ingestionJobs).listAllJobs("CSV_IMPORT", "RUNNING", 5, 10L);
            verify(ingestionJobs).countAllJobs("CSV_IMPORT", "RUNNING");
        }

        @Test
        @DisplayName("When no job exists, then return an empty page that still names the untracked import type")
        void whenNoJobExists_thenReturnEmptyPageWithUntrackedTypes() {
            // Arrange
            givenRows();

            // Act
            ImportJobPageDTO page = adminImportJobService.listJobs(0, 20, null, null);

            // Assert
            assertTrue(page.getJobs().content().isEmpty());
            assertEquals(0, page.getJobs().totalElements());
            assertEquals(List.of(ImportJobType.PROPERTY_IMAGE_CSV_IMPORT), page.getUntrackedJobTypes());
            assertEquals(AdminImportJobServiceImpl.NOTE, page.getNote());
        }

        @Test
        @DisplayName("When a retry of a job is active or its inputs live elsewhere, then it is not retryable and says why")
        void whenRetryActiveOrInputElsewhere_thenNotRetryable() {
            // Arrange
            when(inputStore.scope()).thenReturn("host-a");
            givenRows(row(INGESTION_ID.toString(), "CSV_IMPORT", "ADDRESSES", "FAILED", CREATED_AT, "S3", null, true),
                    row(UUID.randomUUID().toString(), "CSV_IMPORT", "SALES", "INTERRUPTED", CREATED_AT, "LOCAL", "host-b", false),
                    row(UUID.randomUUID().toString(), "PROPERTY_INGESTION", "API_SALES", "RUNNING", CREATED_AT, "NONE", null, false));

            // Act
            List<ImportJobDTO> jobs = adminImportJobService.listJobs(0, 20, null, null).getJobs().content();

            // Assert
            assertEquals(IngestionJobMapper.RETRY_ALREADY_ACTIVE, jobs.get(0).getRetryBlockedReason());
            assertEquals(IngestionJobMapper.INPUT_ON_OTHER_INSTANCE, jobs.get(1).getRetryBlockedReason());
            assertEquals(IngestionJobMapper.JOB_NOT_FINISHED, jobs.get(2).getRetryBlockedReason());
        }

        @Test
        @DisplayName("When page, size, status or type is out of range, then throw IllegalArgumentException without reading jobs")
        void whenParametersInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            assertEquals("page must be 0 or greater", assertThrows(IllegalArgumentException.class,
                    () -> adminImportJobService.listJobs(-1, 20, null, null)).getMessage());
            assertEquals("size must be between 1 and 100", assertThrows(IllegalArgumentException.class,
                    () -> adminImportJobService.listJobs(0, 0, null, null)).getMessage());
            assertThrows(IllegalArgumentException.class, () -> adminImportJobService.listJobs(0, 101, null, null));
            assertEquals("page is too large for size 100", assertThrows(IllegalArgumentException.class,
                    () -> adminImportJobService.listJobs(Integer.MAX_VALUE, 100, null, null)).getMessage());
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> adminImportJobService.listJobs(0, 20, null, "DONE")).getMessage().startsWith("status must be one of"));
            assertThrows(IllegalArgumentException.class,
                    () -> adminImportJobService.listJobs(0, 20, ImportJobType.PROPERTY_IMAGE_CSV_IMPORT, null));
            verifyNoInteractions(imageBackfillClaimService, ingestionJobService, ingestionJobs);
        }

        @Test
        @DisplayName("When page * size is exactly Integer.MAX_VALUE, then accept it")
        void whenPageOffsetIsAtTheLimit_thenAcceptIt() {
            // Arrange
            givenRows();

            // Act & Assert: 2147483647 * 1 is still a valid offset
            assertNotNull(adminImportJobService.listJobs(Integer.MAX_VALUE, 1, null, null));
        }
    }

    @Nested
    @DisplayName("GetJob Tests")
    class GetJob {

        @Test
        @DisplayName("When the ID is numeric, then return the backfill job with the same ID and its per-property results")
        void whenNumericId_thenReturnBackfillJobWithResults() {
            // Arrange
            when(propertyImageBackfillService.get(7L)).thenReturn(backfillJob(7L));

            // Act
            ImportJobDTO job = adminImportJobService.getJob("7");

            // Assert
            assertEquals(ImportJobType.MAPILLARY_IMAGE_BACKFILL, job.getJobType());
            assertEquals("7", job.getId());
            assertEquals(7L, job.getJobId());
            assertEquals("PARTIAL", job.getStatus());
            assertEquals(3, job.getResults().size());
            assertEquals("FAILED", job.getResults().get(2).getStatus());
            assertEquals(Boolean.FALSE, job.getRetryable());
            verifyNoInteractions(ingestionLaunchService);
        }

        @Test
        @DisplayName("When the ID is a UUID, then return the ingestion job with its items")
        void whenUuid_thenReturnIngestionJob() {
            // Arrange
            ImportJobDTO expected = new ImportJobDTO();
            expected.setId(INGESTION_ID.toString());
            when(ingestionLaunchService.get(INGESTION_ID)).thenReturn(expected);

            // Act & Assert
            assertSame(expected, adminImportJobService.getJob(INGESTION_ID.toString()));
            verifyNoInteractions(propertyImageBackfillService);
        }

        @Test
        @DisplayName("When the ID is neither a positive number nor a UUID, then throw IllegalArgumentException")
        void whenIdInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            assertEquals("id must be a positive number", assertThrows(IllegalArgumentException.class,
                    () -> adminImportJobService.getJob("0")).getMessage());
            assertThrows(IllegalArgumentException.class, () -> adminImportJobService.getJob("abc"));
            assertThrows(IllegalArgumentException.class, () -> adminImportJobService.getJob(null));
            verifyNoInteractions(propertyImageBackfillService, ingestionLaunchService);
        }

        @Test
        @DisplayName("When the job does not exist, then propagate the 404")
        void whenJobDoesNotExist_thenPropagate404() {
            // Arrange
            when(propertyImageBackfillService.get(999L))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Image backfill job not found"));

            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> adminImportJobService.getJob("999"));
            assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        }
    }

    @Nested
    @DisplayName("RetryJob Tests")
    class RetryJob {

        @Test
        @DisplayName("When the ID is a UUID, then delegate the retry with the caller")
        void whenUuid_thenDelegateRetry() {
            // Arrange
            ImportJobDTO queued = new ImportJobDTO();
            when(ingestionLaunchService.retry(INGESTION_ID, "admin")).thenReturn(queued);

            // Act & Assert
            assertSame(queued, adminImportJobService.retryJob(INGESTION_ID.toString(), "admin"));
        }

        @Test
        @DisplayName("When the ID is numeric, then refuse with RETRY_NOT_SUPPORTED because backfill jobs cannot be retried")
        void whenNumericId_thenRefuse() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> adminImportJobService.retryJob("7", "admin"));
            assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
            assertTrue(exception.getReason().startsWith(IngestionJobMapper.RETRY_NOT_SUPPORTED));
            assertThrows(IllegalArgumentException.class, () -> adminImportJobService.retryJob("abc", "admin"));
            verifyNoInteractions(ingestionLaunchService);
        }
    }
}
