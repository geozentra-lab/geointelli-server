package com.geointelli.ai.property.service.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.geointelli.ai.property.service.serialization.FlexibleBooleanDeserializer;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OwnerInfo {
    @JsonProperty("Name")
    private String name;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Role")
    private String role;

    @JsonProperty("PercentageOwn")
    private BigDecimal percentageOwn;

    @JsonProperty("ShortDescription")
    private String shortDescription;

    @JsonProperty("TenancyCd")
    private String tenancyCd;

    @JsonProperty("MarriedFlag")
    @JsonDeserialize(using = FlexibleBooleanDeserializer.class)
    private Boolean marriedFlag;

    @JsonProperty("Message")
    private String message;
}
