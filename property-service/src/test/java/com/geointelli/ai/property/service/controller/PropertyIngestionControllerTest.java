package com.geointelli.ai.property.service.controller;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionLaunchService;

@WebMvcTest(PropertyIngestionController.class)
@AutoConfigureMockMvc(addFilters = false)
class PropertyIngestionControllerTest {
    private static final String JOB_ID = "4f1c7d2e-9b2a-4c1e-8f3a-2d9e6b7c1a55";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    IngestionLaunchService launchService;

    private ImportJobDTO queued(IngestionDataset dataset) {
        ImportJobDTO job = new ImportJobDTO();
        job.setId(JOB_ID);
        job.setJobType(ImportJobType.PROPERTY_INGESTION);
        job.setDataset(dataset);
        job.setStatus("QUEUED");
        return job;
    }

    @Test
    @DisplayName("When an ingestion endpoint is called, then its dataset job is queued and 202 points at the job")
    void whenIngestionRequested_thenReturn202() throws Exception {
        when(launchService.startApi(IngestionDataset.API_PROPERTIES, null)).thenReturn(queued(IngestionDataset.API_PROPERTIES));
        when(launchService.startApi(IngestionDataset.API_EXTRA_FEATURES, null)).thenReturn(queued(IngestionDataset.API_EXTRA_FEATURES));

        mockMvc.perform(post("/api/ingestion/run"))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/ingestion/jobs/" + JOB_ID))
            .andExpect(jsonPath("$.jobType").value("PROPERTY_INGESTION"))
            .andExpect(jsonPath("$.dataset").value("API_PROPERTIES"));
        mockMvc.perform(post("/api/ingestion/ingest_extrafeatures"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.dataset").value("API_EXTRA_FEATURES"));
        verify(launchService).startApi(eq(IngestionDataset.API_PROPERTIES), isNull());
    }

    @Test
    @DisplayName("When a job for the dataset is already active, then return 409 with the reason")
    void whenDatasetActive_thenReturn409() throws Exception {
        when(launchService.startApi(IngestionDataset.API_SALES, null)).thenThrow(new ResponseStatusException(
            HttpStatus.CONFLICT, "DATASET_JOB_ACTIVE: an ingestion job for API_SALES is already queued or running"));

        mockMvc.perform(post("/api/ingestion/ingest_sales"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("DATASET_JOB_ACTIVE: an ingestion job for API_SALES is already queued or running"));
    }

    @Test
    @DisplayName("When the Miami-Dade county is not configured, then return 400")
    void whenCountyMissing_thenReturn400() throws Exception {
        when(launchService.startApi(IngestionDataset.API_ADDRESSES, null))
            .thenThrow(new IllegalArgumentException("Configure county 'Miami-Dade' in state 'FL' before importing properties"));

        mockMvc.perform(post("/api/ingestion/ingestaddresses"))
            .andExpect(status().isBadRequest());
    }
}
