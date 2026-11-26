package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.UserDTO;
import com.geointelli.ai.property.service.service.RealtorLicenseVerificationService;
import com.geointelli.ai.property.service.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Finds a user by email for the admin screens. Under /api/admin: bearer token with the realm role admin required. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin users", description = "Find a GeoZentra account by email, for example to open its role grant "
    + "history or revoke a role. Requires the realm role admin.")
public class AdminUserController {

    private final UserService users;
    private final RealtorLicenseVerificationService realtorLicenses;

    @GetMapping
    @Operation(summary = "Find a user by email", description = "Exact email, case ignored; surrounding spaces are "
        + "removed. Returns the account with its id (for /api/admin/users/{userId}/role-grants and the revoke route), "
        + "Keycloak id, names and latest realtor license status. When several rows share the email (an old Keycloak "
        + "account deleted and signed up again), the newest one is returned. An account exists once its owner has "
        + "signed in at least once. 400 missing or malformed email, 404 no account uses it.")
    public UserDTO findByEmail(@RequestParam String email) {
        UserDTO user = users.findByEmail(email);
        user.setRealtorLicenseStatus(realtorLicenses.latestStatusOf(user.getId()).orElse(null));
        return user;
    }
}
