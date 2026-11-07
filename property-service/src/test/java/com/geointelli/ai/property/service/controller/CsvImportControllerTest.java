package com.geointelli.ai.property.service.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionLaunchService;
import com.geointelli.ai.property.service.service.IngestionLaunchService.InputSource;

@WebMvcTest(CsvImportController.class)
@AutoConfigureMockMvc(addFilters = false)
class CsvImportControllerTest {
    private static final String JOB_ID = "4f1c7d2e-9b2a-4c1e-8f3a-2d9e6b7c1a55";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    IngestionLaunchService launchService;

    private final InputSource source = new InputSource() {
        @Override public String name() { return "addresses.csv"; }
        @Override public long size() { return 3; }
        @Override public InputStream open() { return new ByteArrayInputStream(new byte[3]); }
    };

    private ImportJobDTO queued(IngestionDataset dataset) {
        ImportJobDTO job = new ImportJobDTO();
        job.setId(JOB_ID);
        job.setJobType(ImportJobType.CSV_IMPORT);
        job.setDataset(dataset);
        job.setStatus("QUEUED");
        return job;
    }

    @BeforeEach
    void setUp() {
        when(launchService.upload(any(), anyString())).thenReturn(source);
        when(launchService.staged(anyString(), anyString())).thenReturn(source);
        when(launchService.uploadedFolder(any())).thenReturn(List.of(source));
        when(launchService.stagedFolder(anyString())).thenReturn(List.of(source));
    }

    @Test
    @DisplayName("When a CSV file is uploaded, then the job is started for its dataset and 202 points at the job")
    void whenFileUploaded_thenReturn202WithLocation() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "any.csv", "text/csv", "property_id,zip\n".getBytes());
        when(launchService.startCsv(IngestionDataset.EXTRA_FEATURES, "Baker", "FL", List.of(source), null))
            .thenReturn(queued(IngestionDataset.EXTRA_FEATURES));

        mockMvc.perform(multipart("/api/ingestion/import/extra-features").file(file)
                .param("county", "Baker").param("sourceState", "FL"))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/ingestion/jobs/" + JOB_ID))
            .andExpect(jsonPath("$.id").value(JOB_ID))
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.dataset").value("EXTRA_FEATURES"));
        verify(launchService).upload(any(), eq("extra_features.csv"));
    }

    @Test
    @DisplayName("When a staged file is named, then it is used instead of an upload")
    void whenStagedFileNamed_thenUseIt() throws Exception {
        when(launchService.startCsv(IngestionDataset.ADDRESSES, "Baker", "FL", List.of(source), null))
            .thenReturn(queued(IngestionDataset.ADDRESSES));

        mockMvc.perform(post("/api/ingestion/import/addresses")
                .param("stagedFile", "baker/addresses.csv").param("county", "Baker").param("sourceState", "FL"))
            .andExpect(status().isAccepted());
        verify(launchService).staged("baker/addresses.csv", "addresses.csv");
        verify(launchService, never()).upload(any(), anyString());
    }

    @Test
    @DisplayName("When the dataset is unknown, the file is missing or county is missing, then return 400 without starting")
    void whenRequestInvalid_thenReturn400() throws Exception {
        mockMvc.perform(post("/api/ingestion/import/unknown").param("county", "Baker").param("sourceState", "FL"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unknown dataset: unknown"));
        mockMvc.perform(post("/api/ingestion/import/sales").param("county", "Baker").param("sourceState", "FL"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ingestion/import/sales").param("stagedFile", "x.csv").param("sourceState", "FL"))
            .andExpect(status().isBadRequest());
        verify(launchService, never()).startCsv(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("When the service rejects the input, then return 400 with its message")
    void whenServiceRejects_thenReturn400() throws Exception {
        when(launchService.startCsv(any(), any(), any(), any(), any()))
            .thenThrow(new IllegalArgumentException("The file addresses.csv is missing the columns [zip]"));

        mockMvc.perform(post("/api/ingestion/import/addresses")
                .param("stagedFile", "a.csv").param("county", "Baker").param("sourceState", "FL"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("The file addresses.csv is missing the columns [zip]"));
    }

    @Test
    @DisplayName("When owners are imported, then both files are required and both are passed")
    void whenOwnersImported_thenBothFilesPassed() throws Exception {
        MockMultipartFile owners = new MockMultipartFile("ownersFile", "owners.csv", "text/csv", "id\n".getBytes());
        MockMultipartFile links = new MockMultipartFile("propertyOwnersFile", "property_owner.csv", "text/csv", "property_id,owner_id\n".getBytes());
        when(launchService.startCsv(eq(IngestionDataset.OWNERS), eq("Baker"), eq("FL"), argThat(list -> list.size() == 2), isNull()))
            .thenReturn(queued(IngestionDataset.OWNERS));

        mockMvc.perform(multipart("/api/ingestion/import/owners").file(owners).file(links)
                .param("county", "Baker").param("sourceState", "FL"))
            .andExpect(status().isAccepted());
        verify(launchService).upload(any(), eq("owners.csv"));
        verify(launchService).upload(any(), eq("property_owner.csv"));

        mockMvc.perform(multipart("/api/ingestion/import/owners").file(owners)
                .param("county", "Baker").param("sourceState", "FL"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("When a folder is uploaded or staged, then the folder job is started")
    void whenFolderGiven_thenStartFolderJob() throws Exception {
        MockMultipartFile properties = new MockMultipartFile("files", "properties.csv", "text/csv", "id\n".getBytes());
        when(launchService.startCsv(IngestionDataset.FOLDER, "Baker", "FL", List.of(source), null))
            .thenReturn(queued(IngestionDataset.FOLDER));

        mockMvc.perform(multipart("/api/ingestion/import/folder").file(properties)
                .param("county", "Baker").param("sourceState", "FL"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.dataset").value("FOLDER"));
        verify(launchService).uploadedFolder(argThat(files -> files.size() == 1));

        mockMvc.perform(post("/api/ingestion/import/folder")
                .param("stagedFolder", "baker").param("county", "Baker").param("sourceState", "FL"))
            .andExpect(status().isAccepted());
        verify(launchService).stagedFolder("baker");
    }

    @Test
    @DisplayName("When a job is read, then return it, or 404 when unknown")
    void whenJobRead_thenReturnIt() throws Exception {
        when(launchService.get(UUID.fromString(JOB_ID))).thenReturn(queued(IngestionDataset.ADDRESSES));
        mockMvc.perform(get("/api/ingestion/jobs/" + JOB_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(JOB_ID));

        UUID missing = UUID.randomUUID();
        when(launchService.get(missing)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion job not found"));
        mockMvc.perform(get("/api/ingestion/jobs/" + missing)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/ingestion/jobs/not-a-uuid")).andExpect(status().isBadRequest());
    }
}
