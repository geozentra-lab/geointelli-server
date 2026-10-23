package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;
import com.geointelli.ai.property.service.dto.MortgageCalculationResponse;

/**
 * Calculates mortgage payments and monthly ownership costs.
 * Pure calculation: never reads or updates property records.
 */
public interface MortgageCalculationService {

    /**
     * Calculates the monthly principal and interest and itemizes the supplied ownership costs.
     * Unknown costs (tax, insurance, HOA) are returned as null, never assumed to be zero;
     * the total includes only the known costs.
     *
     * @param request price, down payment, annual rate (%), term (years) and optional yearly tax/insurance, monthly HOA
     * @return the loan amount, the monthly cost breakdown and the total of known monthly costs
     * @throws IllegalArgumentException if the down payment exceeds the price
     */
    MortgageCalculationResponse calculateMortgage(MortgageCalculationRequest request);
}
