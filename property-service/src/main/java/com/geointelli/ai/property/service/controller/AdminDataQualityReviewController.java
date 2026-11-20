package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.DataQualityIssueReviewDTO;
import com.geointelli.ai.property.service.dto.DataQualityReviewRequest;
import com.geointelli.ai.property.service.service.DataQualityReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Review and resolution of data-quality findings (SCRUM-92 / SCRUM-190). Lives under /api/admin, so the security
 * configuration already requires a bearer token (else 401) with the Keycloak realm role admin (else 403). The
 * findings themselves come from the scanner report of SCRUM-189 (GET /api/admin/data-quality/issues), which also
 * shows the review status of each finding.
 */
@RestController
@RequestMapping("/api/admin/data-quality/issues")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin data quality", description = "Missing, stale, conflicting, outlier and duplicate records; "
        + "page >= 0, size 1-100. Read-only: nothing is changed or deleted. Invalid input 400.")
public class AdminDataQualityReviewController {
    private final DataQualityReviewService reviews;

    @GetMapping("/{reference}")
    @Operation(summary = "Review state of one finding", description = "Current review status of the finding "
            + "RULE:entityType:entityId (for example SALE_DUPLICATE:SALE:9012) and every review action so far, oldest "
            + "first. A finding never reviewed is OPEN with an empty history. 400 when the reference is malformed or "
            + "names an unknown rule, 404 when the property, sale or assessment does not exist. The finding is not "
            + "re-detected here: the scanner report says whether it is still reported.")
    public DataQualityIssueReviewDTO get(
            @Parameter(description = "Finding reference RULE:entityType:entityId", example = "SALE_DUPLICATE:SALE:9012")
            @PathVariable String reference) {
        return reviews.get(reference);
    }

    @PatchMapping("/{reference}")
    @Operation(summary = "Move a finding to a review status", description = "Records one review action on the "
            + "finding: the target status, the reason, the admin behind the token as reviewer and the time. "
            + "Supported moves: OPEN to IN_REVIEW, RESOLVED or IGNORED; IN_REVIEW to RESOLVED, IGNORED or OPEN; "
            + "RESOLVED or IGNORED to OPEN (reopen). reason is required except for IN_REVIEW (max 500 characters). "
            + "The log is append-only, so earlier reviewers, times and reasons are kept. The property, sale or "
            + "assessment itself is never changed. 400 invalid body or reference, 404 unknown entity, 409 when the "
            + "move is not supported from the current status.")
    public DataQualityIssueReviewDTO review(
            @Parameter(description = "Finding reference RULE:entityType:entityId", example = "SALE_DUPLICATE:SALE:9012")
            @PathVariable String reference,
            @Valid @RequestBody DataQualityReviewRequest request) {
        return reviews.review(reference, request);
    }
}
