package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PropertySearchRequest {
    @Positive
    private Long countyId;
    @Size(max = 100)
    private String state;
    @Size(max = 255)
    private String city;
    @Size(max = 20)
    private String zip;
    @Size(max = 255)
    private String address;
    @Size(max = 255)
    private String neighborhood;
    @Size(max = 255)
    private String propertyType;
    @Size(max = 255)
    private String dorCode;
    @DecimalMin("0")
    private BigDecimal minBedrooms;
    @DecimalMin("0")
    private BigDecimal maxBedrooms;
    @DecimalMin("0")
    private BigDecimal minBathrooms;
    @DecimalMin("0")
    private BigDecimal maxBathrooms;
    @DecimalMin("0")
    private BigDecimal minLivingAreaSqft;
    @DecimalMin("0")
    private BigDecimal maxLivingAreaSqft;
    @Min(1) @Max(9999)
    private Integer minYearBuilt;
    @Min(1) @Max(9999)
    private Integer maxYearBuilt;
    @Min(0)
    private int page = 0;
    @Min(1) @Max(100)
    private int size = 20;
    private String sortBy = "id";
    private String direction = "asc";
}
