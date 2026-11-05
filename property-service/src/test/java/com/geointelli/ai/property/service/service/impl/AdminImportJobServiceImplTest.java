package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.ImageBackfillJobDTO;
import com.geointelli.ai.property.service.dto.ImageBackfillResultDTO;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobPageDTO;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.service.ImageBackfillClaimService;
import com.geointelli.ai.property.service.service.ImageBackfillJobService;
import com.geointelli.ai.property.service.service.PropertyImageBackfillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AdminImportJobServiceImplTest {

    ImageBackfillJobService imageBackfillJobService = mock(ImageBackfillJobService.class);
    ImageBackfillClaimService imageBackfillClaimService = mock(ImageBackfillClaimService.class);
    PropertyImageBackfillService propertyImageBackfillService = mock(PropertyImageBackfillService.class);

    AdminImportJobServiceImpl adminImportJobService = new AdminImportJobServiceImpl(
            imageBackfillJobService, imageBackfillClaimService, propertyImageBackfillService);

    private static final Instant CREATED_AT = Instant.parse("2026-11-05T10:00:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-11-05T10:05:00Z");

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
        job.setErrorCode(null);
        job.setCreatedAt(CREATED_AT);
        job.setFinishedAt(FINISHED_AT);
        job.setResults(List.of(
                new ImageBackfillResultDTO(10L, "COMPLETED", null, 100L),
                new ImageBackfillResultDTO(11L, "SKIPPED", "NO_SUITABLE_IMAGE_OR_PARCEL", null),
                new ImageBackfillResultDTO(12L, "FAILED", "IMAGE_PROCESSING_OR_STORAGE_FAILED", null)));
        return job;
    }

    private void givenStoredJobs(ImageBackfillJobDTO... jobs) {
        when(imageBackfillJobService.list(any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(jobs), call.getArgument(0), jobs.length));
    }

    @Nested
    @DisplayName("ListJobs Tests")
    class ListJobs {

        @Test
        @DisplayName("When jobs exist, then return them with type, status, counters and timestamps but without results")
        void whenJobsExist_thenReturnThemWithoutResults() {
            // Arrange
            givenStoredJobs(backfillJob(7L));

            // Act
            ImportJobPageDTO page = adminImportJobService.listJobs(0, 20);

            // Assert
            assertEquals(1, page.getJobs().content().size());
            ImportJobDTO job = page.getJobs().content().get(0);
            assertEquals(ImportJobType.MAPILLARY_IMAGE_BACKFILL, job.getJobType());
            assertEquals(7L, job.getJobId());
            assertEquals(1L, job.getCountyId());
            assertEquals("PARTIAL", job.getStatus());
            assertEquals(3, job.getTotal());
            assertEquals(3, job.getProcessed());
            assertEquals(1, job.getCompleted());
            assertEquals(1, job.getSkipped());
            assertEquals(1, job.getFailed());
            assertNull(job.getErrorCode());
            assertEquals(CREATED_AT, job.getCreatedAt());
            assertEquals(FINISHED_AT, job.getFinishedAt());
            assertNull(job.getResults());
            assertEquals(1, page.getJobs().totalElements());
        }

        @Test
        @DisplayName("When listing, then interrupt expired jobs first and read the newest jobs first with the requested size")
        void whenListing_thenInterruptExpiredFirstAndSortNewestFirst() {
            // Arrange
            givenStoredJobs();
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

            // Act
            adminImportJobService.listJobs(2, 5);

            // Assert
            InOrder inOrder = inOrder(imageBackfillClaimService, imageBackfillJobService);
            inOrder.verify(imageBackfillClaimService).interruptExpired();
            inOrder.verify(imageBackfillJobService).list(pageable.capture());
            assertEquals(2, pageable.getValue().getPageNumber());
            assertEquals(5, pageable.getValue().getPageSize());
            assertEquals(Sort.by(Sort.Direction.DESC, "id"), pageable.getValue().getSort());
        }

        @Test
        @DisplayName("When no job exists, then return an empty page that still names the untracked import types")
        void whenNoJobExists_thenReturnEmptyPageWithUntrackedTypes() {
            // Arrange
            givenStoredJobs();

            // Act
            ImportJobPageDTO page = adminImportJobService.listJobs(0, 20);

            // Assert
            assertTrue(page.getJobs().content().isEmpty());
            assertEquals(0, page.getJobs().totalElements());
            assertEquals(List.of(ImportJobType.CSV_IMPORT, ImportJobType.PROPERTY_INGESTION), page.getUntrackedJobTypes());
            assertEquals(AdminImportJobServiceImpl.NOTE, page.getNote());
        }

        @Test
        @DisplayName("When size is 100, then accept it")
        void whenSizeIsMaximum_thenAcceptIt() {
            // Arrange
            givenStoredJobs();

            // Act & Assert
            assertDoesNotThrow(() -> adminImportJobService.listJobs(0, AdminImportJobServiceImpl.MAX_PAGE_SIZE));
        }

        @Test
        @DisplayName("When page or size is out of range, then throw IllegalArgumentException without reading jobs")
        void whenPageOrSizeIsInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            IllegalArgumentException page = assertThrows(IllegalArgumentException.class,
                    () -> adminImportJobService.listJobs(-1, 20));
            assertEquals("page must be 0 or greater", page.getMessage());

            IllegalArgumentException tooSmall = assertThrows(IllegalArgumentException.class,
                    () -> adminImportJobService.listJobs(0, 0));
            assertEquals("size must be between 1 and 100", tooSmall.getMessage());

            assertThrows(IllegalArgumentException.class, () -> adminImportJobService.listJobs(0, 101));
            verifyNoInteractions(imageBackfillClaimService, imageBackfillJobService, propertyImageBackfillService);
        }
    }

    @Nested
    @DisplayName("GetJob Tests")
    class GetJob {

        @Test
        @DisplayName("When the job exists, then return it with the same ID and its per-property results")
        void whenJobExists_thenReturnItWithResults() {
            // Arrange
            when(propertyImageBackfillService.get(7L)).thenReturn(backfillJob(7L));

            // Act
            ImportJobDTO job = adminImportJobService.getJob(7L);

            // Assert
            assertEquals(ImportJobType.MAPILLARY_IMAGE_BACKFILL, job.getJobType());
            assertEquals(7L, job.getJobId());
            assertEquals("PARTIAL", job.getStatus());
            assertEquals(3, job.getResults().size());
            assertEquals("FAILED", job.getResults().get(2).getStatus());
            assertEquals("IMAGE_PROCESSING_OR_STORAGE_FAILED", job.getResults().get(2).getReason());
        }

        @Test
        @DisplayName("When the ID is 0 or null, then throw IllegalArgumentException without reading the job")
        void whenIdIsInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> adminImportJobService.getJob(0L));
            assertThrows(IllegalArgumentException.class, () -> adminImportJobService.getJob(null));
            verifyNoInteractions(propertyImageBackfillService);
        }

        @Test
        @DisplayName("When the job does not exist, then let the existing 404 through")
        void whenJobDoesNotExist_thenThrowNotFound() {
            // Arrange
            when(propertyImageBackfillService.get(999L))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Image backfill job not found"));

            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> adminImportJobService.getJob(999L));
            assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        }
    }
}
