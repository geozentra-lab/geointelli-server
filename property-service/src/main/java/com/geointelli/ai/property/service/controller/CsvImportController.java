package com.geointelli.ai.property.service.controller;

import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionLaunchService;
import com.geointelli.ai.property.service.service.IngestionLaunchService.InputSource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Starts CSV import jobs (SCRUM-188). Each endpoint validates the request, stores the uploaded files and
 * returns 202 with the queued job; the import itself runs in the background on any instance.
 */
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
@Tag(name = "Ingestion jobs", description = "Background CSV imports and Miami-Dade ingestion. Every start "
        + "endpoint returns 202 with the job and a Location header; poll GET /api/ingestion/jobs/{id}. "
        + "Files are uploaded as multipart/form-data, or named with stagedFile/stagedFolder inside the "
        + "configured staging directory. Invalid input 400, missing county 400.")
public class CsvImportController {
    private static final String JOB_PATH = "/api/ingestion/jobs/";

    private final IngestionLaunchService launchService;

    /**
     * Starts a folder import: properties.csv first, then the related files, predictions and images last.
     *
     * @param files the county CSV files, named as in a county folder; or
     * @param stagedFolder a folder inside the staging directory holding those files
     * @return 202 with the queued job
     */
    @PostMapping("/import/folder")
    @Operation(summary = "Import a county folder", description = "Requires properties, addresses, assessments, "
            + "buildings, owners, property_owner, lands, sales, taxes and extra_features CSVs; parcels, "
            + "property_value_predictions and property_images are optional. Either upload them as 'files' or "
            + "give 'stagedFolder'. The job commits in batches and can be retried from its checkpoint.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    @ApiResponse(responseCode = "400", description = "Missing file, missing column, empty file or unknown county/state")
    public ResponseEntity<ImportJobDTO> importFolder(
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(required = false) String stagedFolder,
            @RequestParam String county, @RequestParam String sourceState, Principal principal) {
        List<InputSource> inputs = stagedFolder != null && !stagedFolder.isBlank()
                ? launchService.stagedFolder(stagedFolder)
                : launchService.uploadedFolder(files);
        return accepted(launchService.startCsv(IngestionDataset.FOLDER, county, sourceState, inputs, name(principal)));
    }

    /**
     * Starts an owners import from owners.csv and property_owner.csv, which load in one transaction.
     *
     * @return 202 with the queued job
     */
    @PostMapping("/import/owners")
    @Operation(summary = "Import owners", description = "Upload 'ownersFile' and 'propertyOwnersFile', or name "
            + "'stagedOwnersFile' and 'stagedPropertyOwnersFile'. Both files load in one transaction; a retry "
            + "reruns the step.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    @ApiResponse(responseCode = "400", description = "Missing file, missing column, empty file or unknown county/state")
    public ResponseEntity<ImportJobDTO> importOwners(
            @RequestParam(required = false) MultipartFile ownersFile,
            @RequestParam(required = false) MultipartFile propertyOwnersFile,
            @RequestParam(required = false) String stagedOwnersFile,
            @RequestParam(required = false) String stagedPropertyOwnersFile,
            @RequestParam String county, @RequestParam String sourceState, Principal principal) {
        List<InputSource> inputs = new ArrayList<>();
        inputs.add(input(ownersFile, stagedOwnersFile, IngestionDataset.OWNERS.getFileName(), "ownersFile"));
        inputs.add(input(propertyOwnersFile, stagedPropertyOwnersFile, IngestionDataset.PROPERTY_OWNER_FILE,
                "propertyOwnersFile"));
        return accepted(launchService.startCsv(IngestionDataset.OWNERS, county, sourceState, inputs, name(principal)));
    }

    /**
     * Starts a single-file import.
     *
     * @param dataset properties, addresses, assessments, buildings, lands, sales, taxes, extra-features,
     *                parcels, property-value-predictions or property-images
     * @return 202 with the queued job
     */
    @PostMapping("/import/{dataset}")
    @Operation(summary = "Import one CSV file", description = "dataset is one of properties, addresses, "
            + "assessments, buildings, lands, sales, taxes, extra-features, parcels, property-value-predictions, "
            + "property-images. Upload 'file' or name 'stagedFile'. The county must exist unless the dataset is "
            + "properties, which creates it. Existing properties are never overwritten: a duplicate folio fails the job.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    @ApiResponse(responseCode = "400", description = "Unknown dataset, missing file, missing column, empty file or unknown county/state")
    public ResponseEntity<ImportJobDTO> importCsv(
            @Parameter(description = "Dataset path segment, for example extra-features") @PathVariable String dataset,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String stagedFile,
            @RequestParam String county, @RequestParam String sourceState, Principal principal) {
        IngestionDataset target = IngestionDataset.csvFromPathSegment(dataset)
                .orElseThrow(() -> new IllegalArgumentException("Unknown dataset: " + dataset));
        if (target == IngestionDataset.OWNERS) {
            throw new IllegalArgumentException("Use POST /api/ingestion/import/owners for owners");
        }
        List<InputSource> inputs = List.of(input(file, stagedFile, target.getFileName(), "file"));
        return accepted(launchService.startCsv(target, county, sourceState, inputs, name(principal)));
    }

    /**
     * Returns one ingestion job with its failed and skipped records.
     *
     * @param id job UUID from the start response
     * @return 200 with the job; 404 if unknown
     */
    @GetMapping("/jobs/{id}")
    @Operation(summary = "Get an ingestion job", description = "Status, counters, current step, error and the "
            + "first 1000 failed or skipped records. Mapillary backfill jobs are read from their own endpoint.")
    @ApiResponse(responseCode = "200", description = "The job")
    @ApiResponse(responseCode = "404", description = "Unknown job")
    public ResponseEntity<ImportJobDTO> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(launchService.get(id));
    }

    private InputSource input(MultipartFile file, String staged, String targetName, String parameter) {
        if (staged != null && !staged.isBlank()) return launchService.staged(staged, targetName);
        if (file == null) throw new IllegalArgumentException("Upload '" + parameter + "' or name a staged file");
        return launchService.upload(file, targetName);
    }

    static ResponseEntity<ImportJobDTO> accepted(ImportJobDTO job) {
        return ResponseEntity.accepted().location(URI.create(JOB_PATH + job.getId())).body(job);
    }

    static String name(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
