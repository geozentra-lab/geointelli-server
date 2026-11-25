package com.geointelli.ai.property.service.dto;

import java.util.List;

import com.geointelli.ai.property.service.entity.UserPersona;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Effective feature access; resource ownership is checked separately on every
 * request.
 */
public record UserEntitlementsResponse(
        UserPersona persona,
        @Schema(description = "Recognized realm roles; user is implicit for every authenticated account") List<String> roles,
        Features features,
        List<LenderOrganizationDTO> lenderOrganizations,
        @Schema(description = "NOT_CONFIGURED: no billing/subscription grants or restrictions are implemented") String subscriptionPolicy,
        String scopeNote) {

    /**
     * Route-level feature access, independent of the profile's selected persona.
     */
    public record Features(boolean normalUser, boolean investment, boolean realtorCrm, boolean lender,
            boolean administration) {
    }
}
