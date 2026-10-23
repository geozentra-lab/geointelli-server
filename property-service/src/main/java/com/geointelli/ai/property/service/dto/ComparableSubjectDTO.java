package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

/** Current subject facts used to explain the comparison. */
public record ComparableSubjectDTO(Long propertyId, Long countyId, String dorCode,
        BigDecimal bedrooms, BigDecimal bathrooms, BigDecimal livingAreaSqft, String yearBuilt) {
}
