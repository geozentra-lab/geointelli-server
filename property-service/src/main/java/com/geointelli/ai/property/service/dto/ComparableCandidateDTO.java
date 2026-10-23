package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;
import lombok.Data;

/** A candidate's current property facts and recorded qualified transaction. */
@Data
@Builder
public class ComparableCandidateDTO {
    private Long propertyId;
    private String folio;
    private Long countyId;
    private String address;
    private String city;
    private String zip;
    private String dorCode;
    private BigDecimal bedrooms;
    private BigDecimal bathrooms;
    private BigDecimal livingAreaSqft;
    private String yearBuilt;
    private Long saleId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate saleDate;
    private BigDecimal salePrice;
    private String qualifiedFlag;
    private String qualificationDescription;
    private String instrumentNumber;
    private String matchLevel;
    private Double distanceMeters;
}
