package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ScenarioCalculationDTO;

/**
 * Recalculates the current user's saved scenarios (SCRUM-148) with the shared calculation services. The saved
 * assumptions are the only inputs; current property facts are compared, never substituted. Nothing is stored.
 */
public interface ScenarioCalculationService {

    /**
     * Runs the investment calculation (SCRUM-64) on the saved assumptions of one of the current user's scenarios,
     * and reports the linked property's current valuation and the saved inputs that differ from it or cannot be
     * compared.
     *
     * @param id scenario ID
     * @return saved assumptions, current facts, input changes and the calculation result
     * @throws IllegalArgumentException                               if the ID is not positive, or the saved assumptions are not a valid input of
     *                                                                the calculation
     * @throws org.springframework.web.server.ResponseStatusException 404 if the scenario is missing or belongs to
     *                                                                another user
     */
    ScenarioCalculationDTO calculate(Long id);
}
