package com.geointelli.ai.property.service.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Effective feature access; resource ownership is checked separately on every
 * request.
 */
public record UserEntitlementsResponse(
        @Schema(description = "False until the onboarding page was answered (SCRUM-204): show it while false") boolean onboardingCompleted,
        @Schema(description = "When it was answered; null while onboardingCompleted is false") java.time.Instant onboardingCompletedAt,
        @Schema(description = "Recognized realm roles of the token; user is implicit for every authenticated account. "
                + "The professional one (investor, realtor or lender, at most one per account) is the account type")
        List<String> roles,
        Features features,
        @Schema(description = "Roles waiting for an admin decision (investor, lender, or realtor while a license is "
                + "pending review): show 'pending approval' for them (SCRUM-204)") List<String> pendingRoles,
        List<LenderOrganizationDTO> lenderOrganizations,
        @Schema(description = "NOT_CONFIGURED: no billing/subscription grants or restrictions are implemented") String subscriptionPolicy,
        String scopeNote) {

    /**
     * Route-level feature access, from the realm roles of the token.
     */
    public record Features(boolean normalUser, boolean investment, boolean realtorCrm, boolean lender,
            boolean administration) {
    }
}
