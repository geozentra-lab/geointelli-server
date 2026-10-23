package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

/**
 * Child property as returned when browsing a parent's children.
 * Contains only the parent's ID and folio, never a nested parent, so serialization is not recursive.
 */
public record ChildPropertyDTO(
        Long id,

        String folio,

        Long countyId,
        String countyName,

        Long stateId,
        String stateCode,
        String stateName,

        String parentFolio,
        Long parentPropertyId,

        BigDecimal bathroomCount,
        BigDecimal bedroomCount,
        Double halfBathroomCount,

        BigDecimal buildingActualArea,
        BigDecimal buildingBaseArea,
        BigDecimal buildingEffectiveArea,
        BigDecimal buildingGrossArea,
        BigDecimal buildingHeatedArea,

        String dorCode,
        String dorDescription,


        String neighborhood,
        String neighborhoodDescription,

        Double lotSize,
        BigDecimal floorCount,
        Integer unitCount,

        String yearBuilt,

        String municipality,
        String subdivision,

        String primaryZone,
        String primaryZoneDescription,

        String status,

        String showCurrentValuesFlag,
        String message,
        AddressDTO address

) {
}
