package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.UserEntitlementsResponse;
import com.geointelli.ai.property.service.service.UserEntitlementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

/**
 * Frontend capability discovery; the backend still enforces every permission.
 */
@RestController
@RequestMapping("/api/users/me/entitlements")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserEntitlementController {
    private final UserEntitlementService entitlements;

    /**
     * Returns access for the current bearer token, never for a supplied user ID.
     */
    @GetMapping
    @Operation(summary = "Read my effective permissions", description = "All authenticated accounts inherit normal-user "
            + "features. Case-sensitive Keycloak realm roles investor, realtor, lender and admin grant the respective "
            + "features; selected persona and client roles never grant access. Admin feature access retains personal "
            + "ownership and organization membership checks. lenderOrganizations lists only visible organizations. "
            + "Subscription policy is NOT_CONFIGURED: there are no implemented billing-based grants. "
            + "After changing roles in Keycloak, obtain a new access token. Baseline includes property reads, "
            + "valuations, comparisons, favorites, saved searches, alerts, mortgage and affordability calculations. "
            + "Investment covers portfolios, saved investment scenarios, opportunities and investment calculations. "
            + "Admin covers ingestion, backfills, bulk prediction writes and persisted school/transport score rebuilds.")
    @ApiResponse(responseCode = "200", description = "Effective feature access and owned/member organization scope")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    public UserEntitlementsResponse getEntitlements() {
        return entitlements.getEntitlements();
    }
}
