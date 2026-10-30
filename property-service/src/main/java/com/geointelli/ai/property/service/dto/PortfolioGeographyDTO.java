package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
/** Geographic exposure measured by holding count, not by unknown property values. */
public record PortfolioGeographyDTO(Long stateId, String stateCode, String stateName,
    Long countyId, String countyName, boolean unknown, long holdingCount,
    BigDecimal holdingPercent, BigDecimal ownershipEquivalentHoldings) {}
