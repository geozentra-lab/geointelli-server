package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Frozen model inputs, not a list of independently selected comparable properties. */
public record ValuationModelEvidence(String status, String label, LastSale lastSale, NearbySales nearbySales) {
    public record LastSale(BigDecimal price, LocalDate date) { }
    public record NearbySales(BigDecimal medianPrice, Integer count, BigDecimal pricePerSqft,
            BigDecimal radiusDegrees, Integer lookbackDays, LocalDate windowEnd) { }
}
