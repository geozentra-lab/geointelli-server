package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.ImageBackfillResultDTO;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobPageDTO;
import com.geointelli.ai.property.service.dto.IngestionJobItemDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.AdminImportJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminImportJobController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AdminImportJobControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AdminImportJobService adminImportJobService;

    private static final String LIST_URL = "/api/admin/import-jobs";
    private static final String DETAIL_URL = "/api/admin/import-jobs/{id}";
    private static final String RETRY_URL = "/api/admin/import-jobs/{id}/retry";
    private static final String INGESTION_ID = "4f1c7d2e-9b2a-4c1e-8f3a-2d9e6b7c1a55";

    private static ImportJobDTO backfillJob(long id) {
        ImportJobDTO job = new ImportJobDTO();
        job.setId(String.valueOf(id));
        job.setJobType(ImportJobType.MAPILLARY_IMAGE_BACKFILL);
        job.setJobId(id);
        job.setCountyId(1L);
        job.setStatus("COMPLETED");
        job.setTotal(2);
        job.setProcessed(2);
        job.setCompleted(2);
        job.setRetryable(false);
        return job;
    }

    private static ImportJobDTO ingestionJob(String status) {
        ImportJobDTO job = new ImportJobDTO();
        job.setId(INGESTION_ID);
        job.setJobType(ImportJobType.CSV_IMPORT);
        job.setDataset(IngestionDataset.ADDRESSES);
        job.setStatus(status);
        job.setTotal(10);
        job.setProcessed(5);
        job.setCompleted(4);
        job.setFailed(1);
        job.setCurrentStep("addresses.csv");
        job.setErrorCode("INVALID_ROW");
        job.setRetryable(true);
        return job;
    }

    @Nested
    @DisplayName("Test List Import Jobs Endpoint")
    class ListImportJobsTest {

        @Test
        @DisplayName("When no parameter is given, then use page 0 and size 20 without filters and return both kinds of jobs")
        void whenNoParameter_thenUseDefaultsAndReturn200() throws Exception {
            // Arrange
            ImportJobPageDTO page = new ImportJobPageDTO();
            page.setJobs(new PageResponse<>(List.of(ingestionJob("FAILED"), backfillJob(7L)), 0, 20, 2, 1, true, true));
            page.setUntrackedJobTypes(List.of(ImportJobType.PROPERTY_IMAGE_CSV_IMPORT));
            page.setNote("note");
            when(adminImportJobService.listJobs(0, 20, null, null)).thenReturn(page);

            // Act & Assert
            mockMvc.perform(get(LIST_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs.content.length()").value(2))
                    .andExpect(jsonPath("$.jobs.content[0].id").value(INGESTION_ID))
                    .andExpect(jsonPath("$.jobs.content[0].jobType").value("CSV_IMPORT"))
                    .andExpect(jsonPath("$.jobs.content[0].dataset").value("ADDRESSES"))
                    .andExpect(jsonPath("$.jobs.content[0].jobId").doesNotExist())
                    .andExpect(jsonPath("$.jobs.content[0].retryable").value(true))
                    .andExpect(jsonPath("$.jobs.content[0].items").doesNotExist())
                    .andExpect(jsonPath("$.jobs.content[1].jobType").value("MAPILLARY_IMAGE_BACKFILL"))
                    .andExpect(jsonPath("$.jobs.content[1].jobId").value(7))
                    .andExpect(jsonPath("$.jobs.content[1].id").value("7"))
                    .andExpect(jsonPath("$.jobs.content[1].results").doesNotExist())
                    .andExpect(jsonPath("$.jobs.totalElements").value(2))
                    .andExpect(jsonPath("$.untrackedJobTypes[0]").value("PROPERTY_IMAGE_CSV_IMPORT"));
            verify(adminImportJobService).listJobs(0, 20, null, null);
        }

        @Test
        @DisplayName("When type and status are given, then they are passed to the service")
        void whenFiltersGiven_thenPassThem() throws Exception {
            // Arrange
            ImportJobPageDTO page = new ImportJobPageDTO();
            page.setJobs(new PageResponse<>(List.of(), 1, 5, 0, 0, false, true));
            when(adminImportJobService.listJobs(1, 5, ImportJobType.CSV_IMPORT, "RUNNING")).thenReturn(page);

            // Act & Assert
            mockMvc.perform(get(LIST_URL).param("page", "1").param("size", "5")
                            .param("type", "CSV_IMPORT").param("status", "RUNNING"))
                    .andExpect(status().isOk());
            verify(adminImportJobService).listJobs(1, 5, ImportJobType.CSV_IMPORT, "RUNNING");
        }

        @Test
        @DisplayName("When a job has timestamps, then they are returned as ISO-8601 UTC strings")
        void whenJobHasTimestamps_thenReturnIsoStrings() throws Exception {
            // Arrange
            ImportJobDTO job = backfillJob(7L);
            job.setCreatedAt(java.time.Instant.parse("2026-11-05T10:00:00Z"));
            job.setFinishedAt(java.time.Instant.parse("2026-11-05T10:05:00Z"));
            ImportJobPageDTO page = new ImportJobPageDTO();
            page.setJobs(new PageResponse<>(List.of(job), 0, 20, 1, 1, true, true));
            when(adminImportJobService.listJobs(0, 20, null, null)).thenReturn(page);

            // Act & Assert
            mockMvc.perform(get(LIST_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs.content[0].createdAt").value("2026-11-05T10:00:00Z"))
                    .andExpect(jsonPath("$.jobs.content[0].finishedAt").value("2026-11-05T10:05:00Z"));
        }

        @Test
        @DisplayName("When the service rejects the size, then return 400")
        void whenServiceRejectsSize_thenReturn400() throws Exception {
            // Arrange
            when(adminImportJobService.listJobs(0, 101, null, null))
                    .thenThrow(new IllegalArgumentException("size must be between 1 and 100"));

            // Act & Assert
            mockMvc.perform(get(LIST_URL).param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
        }

        @Test
        @DisplayName("When page is not a number or the type is unknown, then return 400 without calling the service")
        void whenPageIsNotANumber_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(LIST_URL).param("page", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            mockMvc.perform(get(LIST_URL).param("type", "NOPE"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(adminImportJobService);
        }
    }

    @Nested
    @DisplayName("Test Get Import Job Endpoint")
    class GetImportJobTest {

        @Test
        @DisplayName("When a numeric ID exists, then return 200 with the backfill results")
        void whenBackfillJobExists_thenReturn200WithResults() throws Exception {
            // Arrange
            ImportJobDTO job = backfillJob(7L);
            job.setResults(List.of(new ImageBackfillResultDTO(10L, "COMPLETED", null, 100L)));
            when(adminImportJobService.getJob("7")).thenReturn(job);

            // Act & Assert
            mockMvc.perform(get(DETAIL_URL, 7))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobId").value(7))
                    .andExpect(jsonPath("$.results.length()").value(1))
                    .andExpect(jsonPath("$.results[0].propertyId").value(10))
                    .andExpect(jsonPath("$.results[0].imageId").value(100));
        }

        @Test
        @DisplayName("When a UUID exists, then return 200 with the failed records")
        void whenIngestionJobExists_thenReturn200WithItems() throws Exception {
            // Arrange
            ImportJobDTO job = ingestionJob("FAILED");
            job.setItems(List.of(new IngestionJobItemDTO("addresses.csv:5", "FAILED", "INVALID_ROW", "bad zip")));
            when(adminImportJobService.getJob(INGESTION_ID)).thenReturn(job);

            // Act & Assert
            mockMvc.perform(get(DETAIL_URL, INGESTION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(INGESTION_ID))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_ROW"))
                    .andExpect(jsonPath("$.currentStep").value("addresses.csv"))
                    .andExpect(jsonPath("$.items[0].itemKey").value("addresses.csv:5"))
                    .andExpect(jsonPath("$.items[0].reason").value("INVALID_ROW"))
                    .andExpect(jsonPath("$.results").doesNotExist());
        }

        @Test
        @DisplayName("When the service rejects the ID, then return 400")
        void whenServiceRejectsId_thenReturn400() throws Exception {
            // Arrange
            when(adminImportJobService.getJob("abc"))
                    .thenThrow(new IllegalArgumentException("id must be a numeric backfill job ID or an ingestion job UUID"));

            // Act & Assert
            mockMvc.perform(get(DETAIL_URL, "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("id must be a numeric backfill job ID or an ingestion job UUID"));
        }

        @Test
        @DisplayName("When the job does not exist, then return 404")
        void whenJobDoesNotExist_thenReturn404() throws Exception {
            // Arrange
            when(adminImportJobService.getJob("999"))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Image backfill job not found"));

            // Act & Assert
            mockMvc.perform(get(DETAIL_URL, 999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Image backfill job not found"));
        }
    }

    @Nested
    @DisplayName("Test Retry Import Job Endpoint")
    class RetryImportJobTest {

        @Test
        @DisplayName("When the retry is accepted, then return 202 with the new job and its Location")
        void whenRetryAccepted_thenReturn202() throws Exception {
            // Arrange
            ImportJobDTO queued = ingestionJob("QUEUED");
            queued.setId("8a9f1b2c-3d4e-4f50-9a6b-7c8d9e0f1a2b");
            queued.setRetryOfJobId(INGESTION_ID);
            queued.setAttempt(2);
            when(adminImportJobService.retryJob(INGESTION_ID, null)).thenReturn(queued);

            // Act & Assert
            mockMvc.perform(post(RETRY_URL, INGESTION_ID))
                    .andExpect(status().isAccepted())
                    .andExpect(header().string("Location", "/api/admin/import-jobs/8a9f1b2c-3d4e-4f50-9a6b-7c8d9e0f1a2b"))
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.retryOfJobId").value(INGESTION_ID))
                    .andExpect(jsonPath("$.attempt").value(2));
            verify(adminImportJobService).retryJob(INGESTION_ID, null);
        }

        @Test
        @DisplayName("When the retry is refused, then return 409 with the reason code in the message")
        void whenRetryRefused_thenReturn409() throws Exception {
            // Arrange
            when(adminImportJobService.retryJob(INGESTION_ID, null)).thenThrow(new ResponseStatusException(
                    HttpStatus.CONFLICT, "RETRY_ALREADY_ACTIVE: a retry of this job is already queued or running"));

            // Act & Assert
            mockMvc.perform(post(RETRY_URL, INGESTION_ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("RETRY_ALREADY_ACTIVE: a retry of this job is already queued or running"));
        }

        @Test
        @DisplayName("When the job does not exist, then return 404")
        void whenJobMissing_thenReturn404() throws Exception {
            // Arrange
            when(adminImportJobService.retryJob(INGESTION_ID, null))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion job not found"));

            // Act & Assert
            mockMvc.perform(post(RETRY_URL, INGESTION_ID)).andExpect(status().isNotFound());
            verify(adminImportJobService).retryJob(eq(INGESTION_ID), isNull());
        }
    }
}
