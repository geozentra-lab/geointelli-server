package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.DataQualityReportDTO;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import com.geointelli.ai.property.service.service.DataQualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin data-quality issues (SCRUM-92 / SCRUM-189). Issues are detected on each request by read-only rules;
 * review and resolution are added by SCRUM-190.
 */
@RestController
@RequestMapping("/api/admin/data-quality")
@RequiredArgsConstructor
@Tag(name = "Admin data quality", description = "Missing, stale, conflicting, outlier and duplicate records; "
        + "page >= 0, size 1-100. Read-only: nothing is changed or deleted. Invalid input 400.")
public class AdminDataQualityController {
    private final DataQualityService dataQualityService;

    /**
     * Data-quality report.
     *
     * @param rule optional rule whose issues are listed
     * @param page zero-based page number of the issues
     * @param size page size, between 1 and 100
     * @return 200 with the summary of every rule and, when a rule is given, one page of its issues;
     *         400 if the rule, page or size is invalid
     */
    @GetMapping("/issues")
    @Operation(summary = "Data-quality issues", description = "Always returns every rule with its category, source, "
            + "description and issue count. With rule, also returns one page of that rule's issues ordered by entity "
            + "ID, each with a reproducible reference RULE:entityType:entityId, the property folio and its provenance "
            + "from property_import_mapping. Issues are computed on each request; no issue returns zero counts and "
            + "an empty page.")
    public ResponseEntity<DataQualityReportDTO> getIssues(@RequestParam(required = false) DataQualityRule rule,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(dataQualityService.getReport(rule, page, size));
    }
}
