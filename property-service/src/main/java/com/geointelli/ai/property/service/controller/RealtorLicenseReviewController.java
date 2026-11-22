package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.RealtorLicenseDecisionRequest;
import com.geointelli.ai.property.service.dto.RealtorLicenseReviewDTO;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;
import com.geointelli.ai.property.service.service.RealtorLicenseReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin review of realtor license submissions (SCRUM-199). Lives under /api/admin, so the security
 * configuration already requires a bearer token (else 401) with the Keycloak realm role admin (else 403).
 */
@RestController
@RequestMapping("/api/admin/realtor-license-verifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin realtor licenses", description = "Review queue and manual decisions on realtor license "
    + "submissions (SCRUM-199). Requires Keycloak realm role admin: 401 without a token, 403 without the role. "
    + "Invalid input 400, unknown submission 404, submission already decided 409.")
public class RealtorLicenseReviewController {
    private final RealtorLicenseReviewService reviews;

    @GetMapping
    @Operation(summary = "List license submissions", description = "Newest first. status is optional "
        + "(PENDING_REVIEW, VERIFIED or REJECTED); without it every submission is listed. page >= 0, size 1-100. "
        + "Each item names the applicant (user ID and account e-mail), the submitted details, the decision and any "
        + "automatic check.")
    public PageResponse<RealtorLicenseReviewDTO> list(
            @Parameter(description = "PENDING_REVIEW, VERIFIED or REJECTED; omit for all")
            @RequestParam(required = false) RealtorLicenseVerificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviews.list(status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one license submission", description = "Full detail of one submission whatever its "
        + "status, including reviewer, decision time, registry source and rejection reason once decided. 404 when "
        + "the ID is unknown.")
    public RealtorLicenseReviewDTO get(@PathVariable Long id) {
        return reviews.get(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending submission", description = "The admin confirmed in the state registry "
        + "that the name matches and the license is active. Body: registrySource (required, max 200, for example "
        + "'Florida DBPR licensee search'); rejectionReason must be absent. The row becomes VERIFIED with decision "
        + "method MANUAL, the current admin as reviewer and the decision time. 409 when the submission was already "
        + "decided; a decision is recorded only once. 409 also when the submitted licenseExpirationDate is before "
        + "today (UTC): the license expired while the submission was pending, so reject it instead.")
    public RealtorLicenseReviewDTO approve(@PathVariable Long id, @Valid @RequestBody RealtorLicenseDecisionRequest request) {
        return reviews.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending submission", description = "Body: registrySource (required, max 200) and "
        + "rejectionReason (required, max 500), which the applicant reads on GET /api/users/me/realtor-license. The "
        + "row becomes REJECTED with decision method MANUAL, the current admin as reviewer and the decision time; the "
        + "applicant may then submit again. 400 without a reason, 409 when the submission was already decided. An "
        + "inconclusive registry check is not a rejection: leave the submission pending.")
    public RealtorLicenseReviewDTO reject(@PathVariable Long id, @Valid @RequestBody RealtorLicenseDecisionRequest request) {
        return reviews.reject(id, request);
    }
}
