package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

public record PropertySearchItem(
        Long id, String folio, Long countyId, String countyName,
        String stateCode, String stateName, String address, String city, String zip,
        String dorCode, String propertyType, BigDecimal bedrooms, BigDecimal bathrooms,
        BigDecimal livingAreaSqft, Double lotSize, String yearBuilt,
        String neighborhood, String neighborhoodDescription) {
}
