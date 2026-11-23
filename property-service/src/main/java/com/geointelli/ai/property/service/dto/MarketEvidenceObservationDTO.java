package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.geointelli.ai.property.service.entity.MarketEvidenceObservation;
import com.geointelli.ai.property.service.entity.MarketEvidenceType;
import com.geointelli.ai.property.service.entity.MarketGeographyLevel;
import com.geointelli.ai.property.service.entity.MarketObservationKind;

/**
 * One market observation as returned by {@code GET /api/market-evidence} (SCRUM-131). Sale figures are
 * present only on SALE rows and asking figures only on ASKING rows; the other group is null. Provenance
 * ({@code source}, {@code sourceDataset}, {@code sourceReleaseDate}, {@code importJobId}) and freshness
 * ({@code importedAt}) travel with every row so a reader can judge the evidence.
 */
public record MarketEvidenceObservationDTO(
        Long id,
        MarketEvidenceType evidenceType,
        MarketObservationKind observationKind,
        String source,
        String sourceDataset,
        LocalDate sourceReleaseDate,
        MarketGeographyLevel geographyLevel,
        String geographyCode,
        String geographyName,
        Long stateId,
        Long countyId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String currency,
        Integer saleCount,
        BigDecimal medianSalePrice,
        BigDecimal averageSalePrice,
        BigDecimal medianSalePricePerSqft,
        Integer activeListingCount,
        Integer newListingCount,
        BigDecimal medianAskingPrice,
        BigDecimal averageAskingPrice,
        BigDecimal medianAskingPricePerSqft,
        BigDecimal medianDaysOnMarket,
        Integer priceReducedCount,
        Instant importedAt,
        String importJobId) {

    /** Copies an entity; the lazy state and county are reduced to their IDs without loading them. */
    public static MarketEvidenceObservationDTO from(MarketEvidenceObservation o) {
        return new MarketEvidenceObservationDTO(
            o.getId(), o.getEvidenceType(), o.getObservationKind(),
            o.getSource(), o.getSourceDataset(), o.getSourceReleaseDate(),
            o.getGeographyLevel(), o.getGeographyCode(), o.getGeographyName(),
            o.getState() == null ? null : o.getState().getId(),
            o.getCounty() == null ? null : o.getCounty().getId(),
            o.getPeriodStart(), o.getPeriodEnd(), o.getCurrency(),
            o.getSaleCount(), o.getMedianSalePrice(), o.getAverageSalePrice(), o.getMedianSalePricePerSqft(),
            o.getActiveListingCount(), o.getNewListingCount(), o.getMedianAskingPrice(), o.getAverageAskingPrice(),
            o.getMedianAskingPricePerSqft(), o.getMedianDaysOnMarket(), o.getPriceReducedCount(),
            o.getImportedAt(), o.getImportJobId());
    }
}
