package com.geointelli.ai.property.service.controller;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.PropertyIdsRequest;
import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionLaunchService;
import com.geointelli.ai.property.service.service.SchoolAnalysisService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/property-school-analysis")
@RequiredArgsConstructor
public class SchoolAnalysisController {
    static final String JOB_PATH = "/api/ingestion/jobs/";

    private final SchoolAnalysisService
            propertySchoolAnalysisService;
    private final IngestionLaunchService launchService;

    @PostMapping("/bulk")
    @Operation(summary = "School analysis of several properties", description = "The v1 fields (distances, schools "
            + "within 1 km and their score) and the v2 fields (assigned schools and school score v2) by property ID; "
            + "a property without any school data is absent.")
    public ResponseEntity<Map<Long, SchoolAnalysisDTO>> getPropertySchoolAnalysisByIds(
            @RequestBody PropertyIdsRequest propertyIds) {
        List<SchoolAnalysisDTO> analyses = propertySchoolAnalysisService
                        .getPropertySchoolAnalysisByIds(propertyIds.getPropertyIds());
        Map<Long, SchoolAnalysisDTO> result = analyses.stream()
                        .collect(Collectors.toMap(SchoolAnalysisDTO::getPropertyId,
                                Function.identity()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/assign")
    @Operation(summary = "Assign the zoned schools of a county, then compute its school score v2 (admin)",
            description = "Queues a background job (SCRUM-203) and returns 202 with the job; follow it with GET "
            + "/api/ingestion/jobs/{id}. Per property with a parcel and per level, the school whose attendance zone "
            + "contains the property, or the nearest zoned public school of the county. Without fips5, every county "
            + "with attendance zones. Only the rows that change are written. Requires migration V49.")
    public ResponseEntity<ImportJobDTO> assign(
            @Parameter(description = "5-digit FIPS code of the county", example = "12086")
            @RequestParam(required = false) String fips5,
            Principal principal) {
        return accepted(launchService.startSchoolScoring(IngestionDataset.SCHOOL_ASSIGNMENT, fips5, name(principal)));
    }

    @PostMapping("/calculate-all")
    @Operation(summary = "Recalculate school scores (admin)", description = "Admin role required for every version. "
            + "version=1 (default) recalculates the v1 score of "
            + "property_school_analysis now and returns 204, as before. version=2 queues a background job "
            + "that computes school score v2 from the stored assignments, for one county (fips5) or every assigned "
            + "county, and returns 202 with the job; use it after a new FLDOE grades load.")
    public ResponseEntity<ImportJobDTO> calculateAndSaveAllScores(
            @Parameter(description = "Version 1 only: also recalculate rows that already have a score")
            @RequestParam(defaultValue = "false") boolean recalculateExisting,
            @Parameter(description = "1 (v1 score, default) or 2 (school score v2)")
            @RequestParam(defaultValue = "1") int version,
            @Parameter(description = "Version 2 only: 5-digit FIPS code of the county", example = "12086")
            @RequestParam(required = false) String fips5,
            Principal principal) {
        if (version == 2) {
            if (recalculateExisting) {
                throw new IllegalArgumentException("recalculateExisting applies to version 1 only: version 2 always "
                        + "recalculates every assigned property and writes only what changed");
            }
            return accepted(launchService.startSchoolScoring(IngestionDataset.SCHOOL_SCORE_V2, fips5, name(principal)));
        }
        if (version != 1) {
            throw new IllegalArgumentException("version must be 1 or 2");
        }
        if (fips5 != null) {
            throw new IllegalArgumentException("fips5 applies to version 2 only");
        }
        propertySchoolAnalysisService.calculateAndSaveAllScores(recalculateExisting);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("{id}/calculate-property-score")
    public ResponseEntity<SchoolAnalysisDTO> calculateAndSavePropertyScore(@PathVariable Long id) {
        SchoolAnalysisDTO result = propertySchoolAnalysisService.calculateAndSaveScore(id);
        return ResponseEntity.ok(result);
    }

    private static ResponseEntity<ImportJobDTO> accepted(ImportJobDTO job) {
        return ResponseEntity.accepted().location(URI.create(JOB_PATH + job.getId())).body(job);
    }

    private static String name(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
