package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Identifies a selected county; display names always come from our database. */
@Data
public class CountyLocationDTO {
    @NotNull
    @Positive
    private Long countyId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String countyName;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String stateCode;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String stateName;
}
