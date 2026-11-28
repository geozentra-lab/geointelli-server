package com.geointelli.ai.property.service.controller;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.LenderMemberLookupDTO;
import com.geointelli.ai.property.service.dto.LenderMemberRequest;
import com.geointelli.ai.property.service.dto.LenderOrganizationDTO;
import com.geointelli.ai.property.service.dto.LenderOrganizationRequest;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import com.geointelli.ai.property.service.util.UserSearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Minimal organization bootstrap and explicit membership management for isolated lender tracking. */
@RestController
@RequestMapping("/api/lender/organizations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Lender organizations", description = "Organization membership is enforced now; persona permissions remain "
    + "separate. Creators own their organization. All members can track applications; only the owner can manage "
    + "membership. No public join by organization ID. Members must already have a local user record.")
public class LenderOrganizationController {
    private final LenderOrganizationService organizations;
    private final CurrentUserService currentUser;

    @PostMapping
    @Operation(summary = "Create an organization", description = "Body: name (1-160). The current user becomes owner; returns 201.")
    public ResponseEntity<LenderOrganizationDTO> create(@RequestBody LenderOrganizationRequest request) {
        Long actor = actor();
        if (request == null) throw new IllegalArgumentException("Request body is required");
        var result = organizations.create(actor, request.name());
        return ResponseEntity.created(URI.create("/api/lender/organizations")).body(result);
    }

    @GetMapping
    @Operation(summary = "List organizations I own or belong to")
    public List<LenderOrganizationDTO> list() { return organizations.list(actor()); }

    @GetMapping("/{organizationId}/members")
    @Operation(summary = "List member user IDs", description = "Includes the owner. Only members of the organization may read this list.")
    public List<Long> members(@PathVariable Long organizationId) {
        return organizations.members(actor(), organizationId);
    }

    @GetMapping("/{organizationId}/members/lookup")
    @Operation(summary = "Find a user to add by email", description = "Owner only. Exact email, case ignored. Returns "
        + "userId (for the add-member call below), email, names and member (true when the account is already the owner "
        + "or a member). The person must have signed in to GeoZentra at least once. 400 missing or malformed email, "
        + "403 member who is not the owner, 404 outsider or no account uses the email.")
    public LenderMemberLookupDTO lookupMember(@PathVariable Long organizationId, @RequestParam String email) {
        return organizations.lookupMember(actor(), organizationId, email);
    }

    @GetMapping("/{organizationId}/members/search")
    @Operation(summary = "Suggest users to add while typing", description = "Owner only. The same accounts, in the "
        + "same order, as the admin search GET /api/admin/users/search: email, full name (\"first last\") or last name "
        + "starts with q, case ignored; surrounding spaces are removed and inner runs of spaces count as one. At most "
        + "size entries (default 10, at most 25), by email (accounts without one last), newest row of each email "
        + "only; an empty array when nothing matches. Each entry is {userId, email, firstName, lastName, member} as "
        + "in GET /members/lookup: userId goes into the add-member call, member is true for the owner and current "
        + "members. The person must have signed in to GeoZentra at least once. 400 q missing, under 2 or over 254 "
        + "characters, size outside 1 to 25; 403 member who is not the owner; 404 outsider.")
    public List<LenderMemberLookupDTO> searchMembers(@PathVariable Long organizationId, @RequestParam String q,
            @RequestParam(defaultValue = "" + UserSearch.DEFAULT_SIZE) int size) {
        return organizations.searchMembers(actor(), organizationId, q, size);
    }

    @PostMapping("/{organizationId}/members")
    @Operation(summary = "Grant access to an existing user", description = "Owner only. Body: userId (find it with GET /members/search while typing, or by exact email with GET /members/lookup). Repeating the same grant returns 204 without duplicating membership.")
    public ResponseEntity<Void> addMember(@PathVariable Long organizationId, @RequestBody LenderMemberRequest request) {
        Long actor = actor();
        if (request == null) throw new IllegalArgumentException("Request body is required");
        organizations.addMember(actor, organizationId, request.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{organizationId}/members/{userId}")
    @Operation(summary = "Revoke member access", description = "Owner only; cannot remove the owner. Retains the organization's applications. Missing membership is a no-op.")
    public ResponseEntity<Void> removeMember(@PathVariable Long organizationId, @PathVariable Long userId) {
        organizations.removeMember(actor(), organizationId, userId);
        return ResponseEntity.noContent().build();
    }

    private Long actor() { return currentUser.getCurrentUserEntity().getId(); }
}

