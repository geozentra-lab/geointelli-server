package com.geointelli.ai.property.service.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.RentEvidenceResponseDTO;
import com.geointelli.ai.property.service.entity.RentEvidenceType;
import com.geointelli.ai.property.service.entity.RentGeographyLevel;
import com.geointelli.ai.property.service.entity.RentPeriod;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionLaunchService;
import com.geointelli.ai.property.service.service.IngestionLaunchService.InputSource;
import com.geointelli.ai.property.service.service.RentEvidenceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Rent evidence (SCRUM-132). Administrators import a provider's rent_evidence.csv as a background job; any
 * authenticated user reads the rent observations of one geography with a coverage report. The import path lives
 * under /api/admin, which the security configuration limits to the admin role; the read needs a valid token. The
 * observations describe markets, not users, so the read has no per-user ownership.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Rent evidence", description = "Asking and achieved rent observations by state, county, ZIP or census "
        + "tract and period, with currency, rent period (monthly or annual) and source. Import is admin-only and "
        + "runs as an ingestion job; poll GET /api/ingestion/jobs/{id}. Repeated imports update the same "
        + "observations; 409 while another rent import is queued or running. Reading needs any valid token. "
        + "Invalid input 400, no token 401, not admin 403.")
public class RentEvidenceController {
    private final IngestionLaunchService launchService;
    private final RentEvidenceQueryService queryService;

    /**
     * Starts a rent evidence import.
     *
     * @param file rent_evidence.csv as multipart upload; or
     * @param stagedFile its name inside the staging directory
     * @param county optional county the file is about, with sourceState; shown on the job
     * @param sourceState optional two-letter state of that county
     * @param principal the authenticated administrator, recorded as the job's creator
     * @return 202 with the queued job and a Location header to its status
     */
    @PostMapping("/admin/rent-evidence/import")
    @Operation(summary = "Import rent evidence (admin)", description = "Upload 'file' or name 'stagedFile'. Required "
            + "columns: source, evidence_type (ASKING or ACHIEVED), geography_level (STATE, COUNTY, ZIP or TRACT), "
            + "geography_code, period_start, period_end, rent_period (MONTHLY or ANNUAL). Each row needs median_rent, "
            + "average_rent or median_rent_per_sqft, all in the row's rent_period; amounts are stored as published, "
            + "never converted. The first invalid row fails the job with its row number; earlier batches stay saved. "
            + "county and sourceState are optional because one file may span many areas. The same file imported "
            + "twice updates the existing rows.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    @ApiResponse(responseCode = "400", description = "Missing file, missing column, empty file or unknown county/state")
    @ApiResponse(responseCode = "401", description = "No token")
    @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    @ApiResponse(responseCode = "409", description = "Another rent evidence import is queued or running, or "
            + "migration V50 is not applied")
    public ResponseEntity<ImportJobDTO> importRentEvidence(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String stagedFile,
            @RequestParam(required = false) String county,
            @RequestParam(required = false) String sourceState,
            Principal principal) {
        String targetName = IngestionDataset.RENT_EVIDENCE.getFileName();
        InputSource input = stagedFile != null && !stagedFile.isBlank()
                ? launchService.staged(stagedFile, targetName)
                : launchService.upload(requireFile(file), targetName);
        return CsvImportController.accepted(launchService.startCsv(IngestionDataset.RENT_EVIDENCE, county,
                sourceState, List.of(input), CsvImportController.name(principal)));
    }

    /**
     * Reads the rent observations of one geography, newest period first, with coverage.
     *
     * @param level STATE, COUNTY, ZIP or TRACT
     * @param code two-letter state, five-digit county FIPS or ZIP, or eleven-digit tract FIPS
     * @param type ASKING or ACHIEVED; both when omitted
     * @param source provider code; all when omitted
     * @param rentPeriod MONTHLY or ANNUAL as published; both when omitted
     * @param from keep periods ending on or after this date
     * @param to keep periods starting on or before this date
     * @param asMonthly true to return ANNUAL rows divided by 12, marked convertedFrom = ANNUAL
     * @param page zero-based page number
     * @param size rows per page, 1 to 200
     * @return 200 with the page and coverage; NO_COVERAGE and an empty page when nothing is imported
     */
    @GetMapping("/rent-evidence")
    @Operation(summary = "Read rent evidence", description = "One geography at a time: level and code are required. "
            + "Filter by type (ASKING or ACHIEVED), source, rentPeriod and an overlapping period window (from, to as "
            + "yyyy-MM-dd). Sorted by period start descending. coverage is AVAILABLE, PARTIAL (missingTypes lists the "
            + "rent types with no rows) or NO_COVERAGE. Amounts are as published; asMonthly=true converts ANNUAL rows "
            + "by 12 and marks them with convertedFrom.")
    @ApiResponse(responseCode = "200", description = "The page and its coverage")
    @ApiResponse(responseCode = "400", description = "Code does not fit the level, inverted window, unknown enum value "
            + "or paging out of range")
    @ApiResponse(responseCode = "401", description = "No token")
    public ResponseEntity<RentEvidenceResponseDTO> find(
            @Parameter(description = "STATE, COUNTY, ZIP or TRACT") @RequestParam RentGeographyLevel level,
            @Parameter(description = "FL, 12086, 33139 or 12086000107") @RequestParam String code,
            @Parameter(description = "ASKING or ACHIEVED") @RequestParam(required = false) RentEvidenceType type,
            @RequestParam(required = false) String source,
            @Parameter(description = "MONTHLY or ANNUAL") @RequestParam(required = false) RentPeriod rentPeriod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean asMonthly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(queryService.find(level, code, type, source, rentPeriod, from, to, asMonthly,
                page, size));
    }

    private static MultipartFile requireFile(MultipartFile file) {
        if (file == null) throw new IllegalArgumentException("Upload 'file' or name a staged file");
        return file;
    }
}
