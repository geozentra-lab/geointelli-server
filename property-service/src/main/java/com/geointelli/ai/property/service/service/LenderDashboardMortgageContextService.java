package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;
import com.geointelli.ai.property.service.dto.MortgageContextCardDTO;

/**
 * Mortgage context card of the lender dashboard (SCRUM-116). Reuses the mortgage calculator
 * (SCRUM-62) on caller-supplied inputs only; it never uses a live mortgage-rate feed and never
 * reads personal or organization records.
 */
public interface LenderDashboardMortgageContextService {

    /**
     * Builds the mortgage context card from the supplied inputs.
     * <p>
     * When no input is supplied (every field is null), the card has {@code status UNAVAILABLE}
     * and null {@code assumptions} and {@code mortgage}; nothing is assumed or invented.
     * Otherwise the mortgage calculator result is returned unchanged with {@code status OK},
     * together with the rate assumptions used (rate, term and their source).
     *
     * @param inputs caller-supplied price, down payment, annual rate (%), term (years) and optional
     *               yearly tax/insurance and monthly HOA; may be entirely empty
     * @return the card; {@code generatedAt} is the calculation date
     * @throws IllegalArgumentException if inputs are partially supplied (a required field is missing),
     *         out of range, or the down payment exceeds the price
     */
    MortgageContextCardDTO getMortgageContextCard(MortgageCalculationRequest inputs);
}
