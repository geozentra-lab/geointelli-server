package com.geointelli.ai.property.service.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.RealtorLicenseExpiryRunDTO;
import com.geointelli.ai.property.service.dto.RoleGrantEventDTO;
import com.geointelli.ai.property.service.dto.RoleRevokeRequest;
import com.geointelli.ai.property.service.service.RoleGrantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Realm roles GeoZentra granted: history, admin revocation and the realtor license expiry run (SCRUM-204). Under
 * /api/admin: bearer token with the realm role admin required.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin role grants", description = "History and revocation of the investor, realtor and lender realm "
    + "roles granted by GeoZentra (SCRUM-204). Requires the realm role admin. Keycloak is changed first; 503 when it "
    + "refuses or cannot be reached (nothing changed).")
public class AdminRoleGrantController {

    private final RoleGrantService roleGrants;

    @GetMapping("/users/{userId}/role-grants")
    @Operation(summary = "Role grant history of a user", description = "Every grant and revocation GeoZentra did for "
        + "this user, newest first: role, action, source (ROLE_REQUEST, SELF_SERVICE, REALTOR_LICENSE, "
        + "ADMIN_REVOCATION, LICENSE_EXPIRY), reason, deciding admin and time. Roles assigned by hand in the Keycloak "
        + "console are not listed. 404 when the user is unknown.")
    public List<RoleGrantEventDTO> history(@PathVariable Long userId) {
        return roleGrants.history(userId);
    }

    @PostMapping("/users/{userId}/roles/{role}/revoke")
    @Operation(summary = "Revoke a realm role", description = "role: investor, realtor or lender (admin is never "
        + "managed here). Body: reason (required, max 500). Removes the role in Keycloak, records the revocation and "
        + "marks the user's approved request for that role REVOKED. Revoking a role the user does not have is "
        + "recorded and changes nothing in Keycloak. The user keeps the role until their access token expires "
        + "(5 minutes). 400 unknown role or missing reason, 404 unknown user, 503 Keycloak.")
    public RoleGrantEventDTO revoke(@PathVariable Long userId, @PathVariable String role,
                                    @Valid @RequestBody RoleRevokeRequest request) {
        return roleGrants.revokeByAdmin(userId, role, request.reason());
    }

    @PostMapping("/role-grants/realtor-license-expiry")
    @Operation(summary = "Run the realtor license expiry now", description = "Revokes the realtor role GeoZentra "
        + "granted to users who no longer have a verified license valid today (UTC). The same run is scheduled daily "
        + "when role-grants.realtor-license-expiry.enabled is true. Returns the counts; failed revocations are retried "
        + "by the next run.")
    public RealtorLicenseExpiryRunDTO runRealtorLicenseExpiry() {
        return roleGrants.revokeExpiredRealtorGrants();
    }
}
