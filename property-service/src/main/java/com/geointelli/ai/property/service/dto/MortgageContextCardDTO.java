package com.geointelli.ai.property.service.dto;

import java.time.LocalDateTime;

/**
 * Mortgage context card of the lender dashboard (SCRUM-116). Reuses the mortgage calculator
 * (SCRUM-62) on caller-supplied inputs only; no live mortgage-rate feed is used and no
 * personal or organization record is read.
 */
public record MortgageContextCardDTO(
        String scope,
        LocalDateTime generatedAt,
        String status,
        String error,
        RateAssumptions assumptions,
        MortgageCalculationResponse mortgage
) {
    public static final String SCOPE_SUPPLIED_INPUTS = "SUPPLIED_INPUTS";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    public static final String STATUS_ERROR = "ERROR";
    public static final String RATE_SOURCE = "Supplied by the caller, not a live market rate";
}
