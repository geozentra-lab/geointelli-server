package com.geointelli.ai.property.service.repository;

import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Rent, price and date facts of one holding, read without loading the property. The dates bound the months its
 * contract rent is projected for; any of them may be null.
 */
public record HoldingRentFacts(
        Long holdingId, HoldingOccupancyStatus occupancy, BigDecimal monthlyRent,
        BigDecimal purchasePrice, BigDecimal share,
        LocalDate acquisitionDate, LocalDate leaseStartDate, LocalDate leaseEndDate
) {
}
