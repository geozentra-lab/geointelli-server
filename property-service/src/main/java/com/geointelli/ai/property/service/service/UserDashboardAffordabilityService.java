package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.AffordabilityCalculationRequest;
import com.geointelli.ai.property.service.dto.AffordabilityCardDTO;

/**
 * Affordability card of the normal-user dashboard (SCRUM-99). Reuses the affordability calculator
 * (SCRUM-63) on caller-supplied inputs; no salary, debt, rate or HOA value is ever assumed and no
 * personal or organization record is read.
 */
public interface UserDashboardAffordabilityService {

    /**
     * Builds the affordability card from the supplied inputs.
     * <p>
     * When a required input (annual gross income, monthly debt payments, down payment, annual rate,
     * loan term) was not supplied, the card has {@code status INCOMPLETE}, lists those inputs in
     * {@code missingInputs} and has no estimate. Otherwise the affordability calculator result is
     * returned unchanged with {@code status OK}, including its assumptions, notes and disclaimer.
     *
     * @param inputs caller-supplied affordability inputs; may be partly or entirely empty
     * @return the card; {@code generatedAt} is the calculation date
     * @throws IllegalArgumentException if a supplied input is out of range (for example a negative
     *         income or a rate above 100)
     */
    AffordabilityCardDTO getAffordabilityCard(AffordabilityCalculationRequest inputs);
}
