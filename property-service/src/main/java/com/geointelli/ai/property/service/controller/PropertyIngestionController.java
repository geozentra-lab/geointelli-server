package com.geointelli.ai.property.service.controller;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionLaunchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts Miami-Dade API ingestion jobs (SCRUM-188). Each endpoint queues one job for its dataset and
 * returns 202; the folios still missing the dataset are selected and loaded in the background.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ingestion")
@Slf4j
@Tag(name = "Ingestion jobs")
public class PropertyIngestionController {
    private final IngestionLaunchService launchService;

    /** Loads the Miami-Dade properties whose parcel folio has no property yet. */
    @PostMapping("/run")
    @Operation(summary = "Ingest missing properties", description = "Queues a job that loads every parcel folio "
            + "without a property from the Miami-Dade API. Existing properties are skipped, never overwritten. "
            + "409 if a job for this dataset is already queued or running.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    @ApiResponse(responseCode = "409", description = "A job for this dataset is already active")
    public ResponseEntity<ImportJobDTO> runIngestion(Principal principal) {
        return start(IngestionDataset.API_PROPERTIES, principal);
    }

    /** Loads buildings for properties that have none. */
    @PostMapping("/ingestbuildings")
    @Operation(summary = "Ingest buildings", description = "Queues a job that loads buildings for Miami-Dade "
            + "properties without them. 409 if already active.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    public ResponseEntity<ImportJobDTO> runBuildingsIngestion(Principal principal) {
        return start(IngestionDataset.API_BUILDINGS, principal);
    }

    /** Loads extra features for properties that have none. */
    @PostMapping("/ingest_extrafeatures")
    @Operation(summary = "Ingest extra features", description = "Queues a job that loads extra features for "
            + "Miami-Dade properties without them. 409 if already active.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    public ResponseEntity<ImportJobDTO> runExtraFeaturesIngestion(Principal principal) {
        return start(IngestionDataset.API_EXTRA_FEATURES, principal);
    }

    /** Loads sales for properties that have none. */
    @PostMapping("/ingest_sales")
    @Operation(summary = "Ingest sales", description = "Queues a job that loads sales for Miami-Dade properties "
            + "without them. 409 if already active.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    public ResponseEntity<ImportJobDTO> runSalesIngestion(Principal principal) {
        return start(IngestionDataset.API_SALES, principal);
    }

    /** Loads the site address for properties that have none. */
    @PostMapping("/ingestaddresses")
    @Operation(summary = "Ingest addresses", description = "Queues a job that loads addresses for Miami-Dade "
            + "properties without one. 409 if already active.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    public ResponseEntity<ImportJobDTO> runAddressesIngestion(Principal principal) {
        return start(IngestionDataset.API_ADDRESSES, principal);
    }

    private ResponseEntity<ImportJobDTO> start(IngestionDataset dataset, Principal principal) {
        log.info("{} ingestion requested via API", dataset);
        return CsvImportController.accepted(launchService.startApi(dataset, CsvImportController.name(principal)));
    }
}
