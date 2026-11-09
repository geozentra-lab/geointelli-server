package com.geointelli.ai.property.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/calculations/investment} (SCRUM-64). Operating inputs are required (monthly rent at
 * least); financing and projection are optional sections. Nothing missing is filled in.
 */
public record InvestmentAnalysisRequest(
        @NotNull @Valid
        InvestmentOperatingRequest operating,

        @Valid
        InvestmentFinancingRequest financing,

        @Valid
        InvestmentProjectionRequest projection
) {
}
