package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.LenderApplicationPipelineCardDTO;

/**
 * Application pipeline card of the lender dashboard (SCRUM-113): counts of the organization's recorded applications
 * by current stage.
 */
public interface LenderApplicationPipelineCardService {

    /**
     * Builds the card for one organization the current user belongs to.
     *
     * @param actor          ID of the current user
     * @param organizationId organization whose applications are counted
     * @return the card with every supported stage, 0 included; status EMPTY when the organization has no application
     * @throws IllegalArgumentException if organizationId is missing or not positive (400)
     * @throws org.springframework.web.server.ResponseStatusException 401 without a user, 404 if the organization does
     *                                                                not exist or the user is not a member
     */
    LenderApplicationPipelineCardDTO getCard(Long actor, Long organizationId);
}
