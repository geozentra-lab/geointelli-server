package com.geointelli.ai.property.service.repository;

import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;

import java.math.BigDecimal;

/** Rent and price facts of one holding, read without loading the property. */
public record HoldingRentFacts(
        Long holdingId, HoldingOccupancyStatus occupancy, BigDecimal monthlyRent,
        BigDecimal purchasePrice, BigDecimal share
) {
}
