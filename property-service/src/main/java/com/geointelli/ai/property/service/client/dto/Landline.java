package com.geointelli.ai.property.service.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Landline {

    @JsonProperty("AdjustedUnitPrice")
    private Double adjustedUnitPrice;

    @JsonProperty("CalculatedValue")
    private BigDecimal calculatedValue;

    @JsonProperty("Depth")
    private Double depth;

    @JsonProperty("FrontFeet")
    private Double frontFeet;

    @JsonProperty("LandUse")
    private String landUse;

    @JsonProperty("LandlineType")
    private String landlineType;

    @JsonProperty("Message")
    private String message;

    @JsonProperty("MuniZone")
    private String muniZone;

    @JsonProperty("MuniZoneDescription")
    private String muniZoneDescription;

    @JsonProperty("PAZoneDescription")
    private String paZoneDescription;

    @JsonProperty("PercentCondition")
    private Double percentCondition;

    @JsonProperty("RollYear")
    private Integer rollYear;

    @JsonProperty("TotalAdjustments")
    private String totalAdjustments;

    @JsonProperty("UnitType")
    private String unitType;

    @JsonProperty("Units")
    private Double units;

    @JsonProperty("UseCode")
    private String useCode;

    @JsonProperty("Zone")
    private String zone;
}

