package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.RoleRequestDecisionRequest;
import com.geointelli.ai.property.service.dto.RoleRequestReviewDTO;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;
import com.geointelli.ai.property.service.service.RoleRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin review of investor and lender role requests (SCRUM-204). Lives under /api/admin, so the security
 * configuration requires a bearer token (else 401) with the Keycloak realm role admin (else 403).
 */
@RestController
@RequestMapping("/api/admin/role-requests")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin role requests", description = "Review queue and decisions on investor and lender role requests "
    + "(SCRUM-204). Requires the realm role admin. Approval grants the realm role in Keycloak first; if Keycloak "
    + "refuses or cannot be reached the answer is 503 and the request stays PENDING.")
public class AdminRoleRequestController {

    private final RoleRequestService roleRequests;

    @GetMapping
    @Operation(summary = "List role requests", description = "Newest first. Optional filters: status (PENDING, "
        + "APPROVED, REJECTED, CANCELLED, REVOKED) and role (investor or lender). page >= 0, size 1-100. Each item "
        + "names the applicant (user ID and account e-mail).")
    public PageResponse<RoleRequestReviewDTO> list(
            @Parameter(description = "PENDING, APPROVED, REJECTED, CANCELLED or REVOKED; omit for all")
            @RequestParam(required = false) RoleRequestStatus status,
            @Parameter(description = "investor or lender; omit for both")
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return roleRequests.list(status, role, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one role request", description = "404 when the ID is unknown.")
    public RoleRequestReviewDTO get(@PathVariable Long id) {
        return roleRequests.get(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending role request", description = "Grants the realm role in Keycloak, records "
        + "the grant, then marks the request APPROVED with the current admin as decider. Body optional: reason (max "
        + "500). 409 when already decided; 503 when Keycloak refused or is unreachable (nothing changed). The "
        + "applicant gets the role with their next access token.")
    public RoleRequestReviewDTO approve(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) RoleRequestDecisionRequest request) {
        return roleRequests.approve(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending role request", description = "Body: reason (required, max 500), shown to "
        + "the applicant, who may request again. Keycloak is not called. 400 without a reason, 409 when already "
        + "decided.")
    public RoleRequestReviewDTO reject(@PathVariable Long id, @Valid @RequestBody RoleRequestDecisionRequest request) {
        return roleRequests.reject(id, request.reason());
    }
}
