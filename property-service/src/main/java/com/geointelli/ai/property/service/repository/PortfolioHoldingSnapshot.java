package com.geointelli.ai.property.service.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.geointelli.ai.property.service.entity.HoldingFinancingType;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
/** Scalar holding facts avoid hydrating complete properties and their child collections. */
public record PortfolioHoldingSnapshot(Long holdingId, Long propertyId, BigDecimal share,
    HoldingFinancingType financingType, BigDecimal outstandingBalance, LocalDate balanceDate,
    HoldingOccupancyStatus occupancy, Long countyId, String countyName, Long stateId,
    String stateCode, String stateName, LocalDateTime updatedAt) {}
