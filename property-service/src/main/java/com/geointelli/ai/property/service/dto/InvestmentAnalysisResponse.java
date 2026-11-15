package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of {@code POST /api/calculations/investment} (SCRUM-64). Current returns are split into the unfinanced
 * {@code operating} result and the financed {@code financing} metrics; {@code projection} is the labelled
 * future estimate, null when no projection was requested.
 */
public record InvestmentAnalysisResponse(
        InvestmentOperatingResult operating,
        InvestmentFinancingResult financing,
        BigDecimal grossYieldPercent,
        InvestmentProjectionResult projection,
        List<String> notes
) {
}
