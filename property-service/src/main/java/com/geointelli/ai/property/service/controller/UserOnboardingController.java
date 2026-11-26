package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.UserEntitlementsResponse;
import com.geointelli.ai.property.service.service.OnboardingService;
import com.geointelli.ai.property.service.service.UserEntitlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Onboarding page of SCRUM-204. Investor and lender are chosen with POST /api/users/me/role-requests, realtor with
 * POST /api/users/me/realtor-license; both also answer the page. This endpoint is the home buyer choice.
 */
@RestController
@RequestMapping("/api/users/me/onboarding")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Onboarding", description = "First-login page: the account type is a Keycloak realm role (SCRUM-204)")
public class UserOnboardingController {

    private final OnboardingService onboarding;
    private final UserEntitlementService entitlements;

    @PostMapping("/complete")
    @Operation(summary = "Continue as a home buyer", description = "Marks the onboarding page as answered without a "
        + "professional role (the first time is kept) and returns the entitlements, now with onboardingCompleted "
        + "true. The user can still choose investor, lender or realtor later.")
    public UserEntitlementsResponse complete() {
        onboarding.completeWithoutRole();
        return entitlements.getEntitlements();
    }
}
