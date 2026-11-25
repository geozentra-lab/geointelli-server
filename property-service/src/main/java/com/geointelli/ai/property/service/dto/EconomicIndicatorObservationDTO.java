package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One stored value of an economic indicator (SCRUM-133) with its provenance and revision history.
 *
 * @param period          2026-03 for a monthly value, 2024 for an annual one
 * @param observationKind ACTUAL (observed by the source) or ESTIMATE (modelled or preliminary)
 * @param sourceVintage   BLS: date the API was read; Census: vintage year
 * @param previousValue   value before the last revision; null if never revised
 * @param revisionCount   how many imports changed the value or its kind
 * @param ingestedAt      first import of this period
 * @param revisedAt       last change; null if never revised
 * @param lastVerifiedAt  last import that read this period (freshness)
 */
public record EconomicIndicatorObservationDTO(
        String geographyCode, String geographyName, String period,
        LocalDate periodStart, LocalDate periodEnd, BigDecimal value,
        String observationKind, String sourceSeriesId, String sourceVintage,
        String sourceUrl, BigDecimal previousValue, int revisionCount,
        Instant ingestedAt, Instant revisedAt, Instant lastVerifiedAt
) {
}
