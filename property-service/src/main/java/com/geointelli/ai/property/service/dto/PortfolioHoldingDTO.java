package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Holding facts supplied by the owner; missing values stay null and are not analytical estimates. */
public record PortfolioHoldingDTO(Long id, Long portfolioId, Long propertyId, String folio,
    Long countyId, String currency,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate acquisitionDate,
    BigDecimal purchasePrice, BigDecimal acquisitionCosts, BigDecimal ownershipSharePercent,
    HoldingFinancingDTO financing, HoldingOccupancyDTO occupancy,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime updatedAt) {
}
