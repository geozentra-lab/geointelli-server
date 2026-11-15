package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.LenderCollateralAnalysisDTO;

/**
 * Collateral value and loan-to-value of a lender application (SCRUM-180). Reads the application of an organization
 * the user belongs to and the stored model valuation of its property (SCRUM-58); nothing is changed and no credit
 * decision is made.
 */
public interface LenderCollateralAnalysisService {

    /**
     * Returns the loan amount (funded, else approved, else requested), the collateral valuation with its date and
     * confidence, and the LTV. The LTV is LIMITED for a limited valuation and UNAVAILABLE, with a null percent and a
     * reason, when there is no property, no property found or no usable valuation.
     *
     * @param actor          the authenticated user
     * @param organizationId the organization the application belongs to
     * @param applicationId  the application to analyse
     * @return the analysis, always with the disclaimer
     * @throws IllegalArgumentException if organizationId or applicationId is not positive
     * @throws org.springframework.web.server.ResponseStatusException 404 if the organization or the application is
     *         missing, or the user is not a member
     */
    LenderCollateralAnalysisDTO analyze(Long actor, Long organizationId, Long applicationId);
}
