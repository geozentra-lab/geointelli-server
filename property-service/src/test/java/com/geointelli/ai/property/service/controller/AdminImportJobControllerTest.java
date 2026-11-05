package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.ImageBackfillResultDTO;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobPageDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.enums.ImportJobType;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private static ImportJobDTO job(long id) {
        ImportJobDTO job = new ImportJobDTO();
        job.setJobType(ImportJobType.MAPILLARY_IMAGE_BACKFILL);
        job.setJobId(id);
        job.setCountyId(1L);
        job.setStatus("COMPLETED");
        job.setTotal(2);
        job.setProcessed(2);
        job.setCompleted(2);
        return job;
    }

    @Nested
    @DisplayName("Test List Import Jobs Endpoint")
    class ListImportJobsTest {

        @Test
        @DisplayName("When no parameter is given, then use page 0 and size 20 and return 200 without results")
        void whenNoParameter_thenUseDefaultsAndReturn200() throws Exception {
            // Arrange
            ImportJobPageDTO page = new ImportJobPageDTO();
            page.setJobs(new PageResponse<>(List.of(job(7L)), 0, 20, 1, 1, true, true));
            page.setUntrackedJobTypes(List.of(ImportJobType.CSV_IMPORT, ImportJobType.PROPERTY_INGESTION));
            page.setNote("note");
            when(adminImportJobService.listJobs(0, 20)).thenReturn(page);

            // Act & Assert
            mockMvc.perform(get(LIST_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs.content.length()").value(1))
                    .andExpect(jsonPath("$.jobs.content[0].jobType").value("MAPILLARY_IMAGE_BACKFILL"))
                    .andExpect(jsonPath("$.jobs.content[0].jobId").value(7))
                    .andExpect(jsonPath("$.jobs.content[0].status").value("COMPLETED"))
                    .andExpect(jsonPath("$.jobs.content[0].results").doesNotExist())
                    .andExpect(jsonPath("$.jobs.totalElements").value(1))
                    .andExpect(jsonPath("$.untrackedJobTypes[0]").value("CSV_IMPORT"))
                    .andExpect(jsonPath("$.untrackedJobTypes[1]").value("PROPERTY_INGESTION"));
            verify(adminImportJobService).listJobs(0, 20);
        }

        @Test
        @DisplayName("When the service rejects the size, then return 400")
        void whenServiceRejectsSize_thenReturn400() throws Exception {
            // Arrange
            when(adminImportJobService.listJobs(0, 101))
                    .thenThrow(new IllegalArgumentException("size must be between 1 and 100"));

            // Act & Assert
            mockMvc.perform(get(LIST_URL).param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
        }

        @Test
        @DisplayName("When page is not a number, then return 400 without calling the service")
        void whenPageIsNotANumber_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(LIST_URL).param("page", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(adminImportJobService);
        }
    }

    @Nested
    @DisplayName("Test Get Import Job Endpoint")
    class GetImportJobTest {

        @Test
        @DisplayName("When the job exists, then return 200 with its results")
        void whenJobExists_thenReturn200WithResults() throws Exception {
            // Arrange
            ImportJobDTO job = job(7L);
            job.setResults(List.of(new ImageBackfillResultDTO(10L, "COMPLETED", null, 100L)));
            when(adminImportJobService.getJob(7L)).thenReturn(job);

            // Act & Assert
            mockMvc.perform(get(DETAIL_URL, 7))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobId").value(7))
                    .andExpect(jsonPath("$.results.length()").value(1))
                    .andExpect(jsonPath("$.results[0].propertyId").value(10))
                    .andExpect(jsonPath("$.results[0].imageId").value(100));
        }

        @Test
        @DisplayName("When the service rejects the ID, then return 400")
        void whenServiceRejectsId_thenReturn400() throws Exception {
            // Arrange
            when(adminImportJobService.getJob(0L))
                    .thenThrow(new IllegalArgumentException("id must be a positive number"));

            // Act & Assert
            mockMvc.perform(get(DETAIL_URL, 0))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("id must be a positive number"));
        }

        @Test
        @DisplayName("When the job does not exist, then return 404")
        void whenJobDoesNotExist_thenReturn404() throws Exception {
            // Arrange
            when(adminImportJobService.getJob(999L))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Image backfill job not found"));

            // Act & Assert
            mockMvc.perform(get(DETAIL_URL, 999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Image backfill job not found"));
        }

        @Test
        @DisplayName("When the ID is not a number, then return 400 without calling the service")
        void whenIdIsNotANumber_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(DETAIL_URL, "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(adminImportJobService);
        }
    }
}
