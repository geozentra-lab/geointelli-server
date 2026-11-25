package com.geointelli.ai.property.service.controller;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.RoleRequestCreateRequest;
import com.geointelli.ai.property.service.dto.RoleRequestDTO;
import com.geointelli.ai.property.service.service.RoleRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The current user's requests for the investor and lender realm roles, made from the onboarding page
 * (SCRUM-204). Under /api/users/me, so any signed-in account may call it (401 without a token); the owner is always
 * the token's user.
 */
@RestController
@RequestMapping("/api/users/me/role-requests")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Role requests", description = "Request the investor or lender role (SCRUM-204). An admin approves the "
    + "request (unless the role is listed in onboarding.self-service-roles, then it is granted at once); refresh the "
    + "access token afterwards. Realtor "
    + "access comes from an approved license: POST /api/users/me/realtor-license.")
public class UserRoleRequestController {

    private final RoleRequestService roleRequests;

    @PostMapping
    @Operation(summary = "Request the investor or lender role", description = "Body: role (investor or lender), "
        + "organizationName (optional, max 160), note (optional, max 1000). 201 with the request: PENDING until an "
        + "admin approves it (the default for both roles), or APPROVED at once for a role listed in "
        + "onboarding.self-service-roles (refresh the access token; PENDING when Keycloak could not be reached). 400 "
        + "for realtor (use the license submission) or another role. "
        + "409 when you already have the role's features, a request for that role is pending, or the account already "
        + "has or waits for another professional role (one per account: investor, realtor or lender). Also answers "
        + "the onboarding page.")
    public ResponseEntity<RoleRequestDTO> create(@Valid @RequestBody RoleRequestCreateRequest request) {
        RoleRequestDTO created = roleRequests.create(request);
        return ResponseEntity.created(URI.create("/api/users/me/role-requests/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "List my role requests", description = "Every request of the current user, newest first, "
        + "with the admin's reason once decided.")
    public List<RoleRequestDTO> list() {
        return roleRequests.listMine();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one of my role requests", description = "404 when missing or another user's.")
    public RoleRequestDTO get(@PathVariable Long id) {
        return roleRequests.getMine(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Withdraw a pending role request", description = "The request becomes CANCELLED. 404 when "
        + "missing or another user's, 409 when it was already decided.")
    public RoleRequestDTO cancel(@PathVariable Long id) {
        return roleRequests.cancelMine(id);
    }
}
