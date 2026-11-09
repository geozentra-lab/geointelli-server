package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.InvestmentFinancingRequest;
import com.geointelli.ai.property.service.dto.InvestmentFinancingResult;
import com.geointelli.ai.property.service.dto.InvestmentProjectionRequest;
import com.geointelli.ai.property.service.dto.InvestmentProjectionResult;

/**
 * Projects the equity of an investment property at the end of a horizon (SCRUM-146), from the current financed
 * result. Pure calculation: never reads or updates property records and never invents market appreciation.
 */
public interface InvestmentProjectionService {
    /**
     * Calculates the loan balance, the equity (down payment + principal repaid + appreciation) and the cumulative
     * cash flow after {@code horizonYears}. The property value is projected only with an appreciation rate
     * supplied by the user. A value whose inputs are missing is null with a note; every hypothesis is listed
     * in the assumptions.
     *
     * @param request         horizon (1 to 30 years) and optional annual appreciation rate (%)
     * @param financing       financing inputs as supplied (may be null): purchase price, down payment, rate, term
     * @param financingResult current financed metrics: loan amount and annual cash flow
     * @return the labelled projection with its assumptions and notes
     * @throws IllegalArgumentException if the horizon is missing or out of range, or the rate is out of range
     */
    InvestmentProjectionResult project(InvestmentProjectionRequest request, InvestmentFinancingRequest financing,
                                       InvestmentFinancingResult financingResult);
}
