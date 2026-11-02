package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.AffordabilityCalculationRequest;
import com.geointelli.ai.property.service.dto.AffordabilityCalculationResponse;

/**
 * Estimates how much a buyer can afford.
 * Pure calculation: never reads or updates property records.
 */
public interface AffordabilityCalculationService {

    /**
     * Estimates the maximum monthly housing payment, loan amount and home price using the 28/36 rule
     * (housing at most 28% and housing plus existing debts at most 36% of gross monthly income)
     * and the shared mortgage amortization formula.
     * Missing property tax, insurance or HOA fees are not assumed: they are listed in the notes.
     * The result is an estimate, not a loan approval.
     *
     * @param request yearly gross income, monthly debts, down payment, annual rate (%), term (years)
     *                and optional yearly tax/insurance, monthly HOA
     * @return the affordability estimate, the assumptions used, notes on missing input and a disclaimer
     */
    AffordabilityCalculationResponse calculateAffordability(AffordabilityCalculationRequest request);
}
