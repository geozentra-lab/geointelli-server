package com.geointelli.ai.property.service.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode 
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtraFeatureInfo {

    @JsonProperty("ActualYearBuilt")
    private Integer actualYearBuilt;

    @JsonProperty("AdjustedUnitPrice")
    private Double adjustedUnitPrice;

    @JsonProperty("DepreciatedValue")
    private BigDecimal depreciatedValue;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Message")
    private String message;

    @JsonProperty("PercentCondition")
    private Double percentCondition;

    @JsonProperty("RollYear")
    private Integer rollYear;

    @JsonProperty("Units")
    private Integer units;

    @JsonProperty("UseCode")
    private String useCode;
}