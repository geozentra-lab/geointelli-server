package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.geointelli.ai.property.service.entity.RentEvidenceType;
import com.geointelli.ai.property.service.entity.RentGeographyLevel;
import com.geointelli.ai.property.service.entity.RentObservationKind;
import com.geointelli.ai.property.service.entity.RentPeriod;

/**
 * One rent observation as returned by {@code GET /api/rent-evidence} (SCRUM-132). Every amount is per
 * {@code rentPeriod}. Amounts are as published unless the caller asked for monthly values: an ANNUAL row is then
 * divided by 12, {@code rentPeriod} reads MONTHLY and {@code convertedFrom} reads ANNUAL, so a converted value is
 * never mistaken for a published one. Provenance ({@code source}, {@code sourceDataset},
 * {@code sourceReleaseDate}, {@code importJobId}) and freshness ({@code importedAt}) travel with every row.
 */
public record RentEvidenceObservationDTO(
        Long id,
        RentEvidenceType evidenceType,
        RentObservationKind observationKind,
        String source,
        String sourceDataset,
        LocalDate sourceReleaseDate,
        RentGeographyLevel geographyLevel,
        String geographyCode,
        String geographyName,
        Long stateId,
        Long countyId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String currency,
        RentPeriod rentPeriod,
        /** The published rent period when the amounts were converted for this response; null when not converted. */
        RentPeriod convertedFrom,
        /** Bedrooms the figures refer to; null means all sizes together. */
        Short bedrooms,
        BigDecimal medianRent,
        BigDecimal averageRent,
        BigDecimal medianRentPerSqft,
        BigDecimal medianRentMoe,
        Integer sampleCount,
        Instant importedAt,
        String importJobId) {
}
