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
import com.geointelli.ai.property.service.dto.MarketEvidenceObservationDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.entity.MarketEvidenceType;
import com.geointelli.ai.property.service.entity.MarketGeographyLevel;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionLaunchService;
import com.geointelli.ai.property.service.service.IngestionLaunchService.InputSource;
import com.geointelli.ai.property.service.service.MarketEvidenceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Market transaction and inventory evidence (SCRUM-131). Administrators import a provider's
 * market_evidence.csv as a background job; any authenticated caller reads the observations of one market.
 * The import path lives under /api/admin, which the security configuration limits to the admin role.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Market evidence", description = "Sale and asking-price observations by state, county or ZIP and "
        + "period, with their source and freshness. Import is admin-only and runs as an ingestion job; poll "
        + "GET /api/ingestion/jobs/{id}. Repeated imports update the same observations. Invalid input 400, "
        + "no token 401, not admin 403.")
public class MarketEvidenceController {
    private final IngestionLaunchService launchService;
    private final MarketEvidenceQueryService queryService;

    /**
     * Starts a market evidence import.
     *
     * @param file market_evidence.csv as multipart upload; or
     * @param stagedFile its name inside the staging directory
     * @param county optional county the file is about, with sourceState; shown on the job
     * @param sourceState optional two-letter state of that county
     * @return 202 with the queued job and a Location header to its status
     */
    @PostMapping("/admin/market-evidence/import")
    @Operation(summary = "Import market evidence (admin)", description = "Upload 'file' or name 'stagedFile'. "
            + "Required columns: source, evidence_type (SALE or ASKING), geography_level (STATE, COUNTY or ZIP), "
            + "geography_code, period_start, period_end. SALE rows carry sale_count and sale prices; ASKING rows "
            + "carry listing counts, asking prices and days on market; a row mixing both fails the job. county and "
            + "sourceState are optional because one file may span many markets. The same file imported twice "
            + "updates the existing rows.")
    @ApiResponse(responseCode = "202", description = "Job queued")
    @ApiResponse(responseCode = "400", description = "Missing file, missing column, empty file or unknown county/state")
    @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    public ResponseEntity<ImportJobDTO> importMarketEvidence(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String stagedFile,
            @RequestParam(required = false) String county,
            @RequestParam(required = false) String sourceState,
            Principal principal) {
        String targetName = IngestionDataset.MARKET_EVIDENCE.getFileName();
        InputSource input = stagedFile != null && !stagedFile.isBlank()
                ? launchService.staged(stagedFile, targetName)
                : launchService.upload(requireFile(file), targetName);
        return CsvImportController.accepted(launchService.startCsv(IngestionDataset.MARKET_EVIDENCE, county,
                sourceState, List.of(input), CsvImportController.name(principal)));
    }

    /**
     * Reads the observations of one market, newest period first.
     *
     * @param level STATE, COUNTY or ZIP
     * @param code two-letter state, five-digit county FIPS or five-digit ZIP
     * @param type SALE or ASKING; both when omitted
     * @param source provider code; all when omitted
     * @param from keep periods ending on or after this date
     * @param to keep periods starting on or before this date
     * @param page zero-based page number
     * @param size rows per page, 1 to 200
     * @return 200 with the page; empty content when nothing is imported for the market
     */
    @GetMapping("/market-evidence")
    @Operation(summary = "Read market evidence", description = "One market at a time: level and code are required. "
            + "Filter by type (SALE or ASKING), source and an overlapping period window (from, to as yyyy-MM-dd). "
            + "Sorted by period start descending. A market with no imported rows returns an empty page.")
    @ApiResponse(responseCode = "200", description = "The page")
    @ApiResponse(responseCode = "400", description = "Code does not fit the level, inverted window or paging out of range")
    public ResponseEntity<PageResponse<MarketEvidenceObservationDTO>> find(
            @Parameter(description = "STATE, COUNTY or ZIP") @RequestParam MarketGeographyLevel level,
            @Parameter(description = "FL, 12086 or 33139") @RequestParam String code,
            @Parameter(description = "SALE or ASKING") @RequestParam(required = false) MarketEvidenceType type,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(queryService.find(level, code, type, source, from, to, page, size));
    }

    private static MultipartFile requireFile(MultipartFile file) {
        if (file == null) throw new IllegalArgumentException("Upload 'file' or name a staged file");
        return file;
    }
}
