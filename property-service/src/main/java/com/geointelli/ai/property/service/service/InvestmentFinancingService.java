package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.InvestmentFinancingRequest;
import com.geointelli.ai.property.service.dto.InvestmentFinancingResult;
import com.geointelli.ai.property.service.dto.InvestmentOperatingResult;

/**
 * Calculates the financed metrics of an investment property from its unfinanced operating
 * result (NOI) and the financing inputs. Reuses the mortgage calculator for the debt service.
 */
public interface InvestmentFinancingService {

    /**
     * Calculates debt service, cash flow, cap rate and cash-on-cash.
     * <ul>
     *   <li>Debt service: monthly principal and interest from the mortgage calculator, times 12.</li>
     *   <li>Cash flow: annual NOI minus annual debt service; may be negative.</li>
     *   <li>Cap rate: annual NOI / purchase price × 100 (denominator: purchase price).</li>
     *   <li>Cash-on-cash: annual cash flow / cash invested × 100
     *       (denominator: down payment + closing costs).</li>
     * </ul>
     * A metric whose inputs or denominator are missing (or zero) is null with a note; nothing is assumed.
     *
     * @param request   financing inputs; every field is optional
     * @param operating unfinanced operating result; its NOI is used unchanged
     * @return the financed metrics with one note per unavailable metric
     * @throws IllegalArgumentException if the down payment exceeds the purchase price
     */
    InvestmentFinancingResult calculateFinancingMetrics(InvestmentFinancingRequest request,
                                                        InvestmentOperatingResult operating);
}
