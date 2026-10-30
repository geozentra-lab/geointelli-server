package com.geointelli.ai.property.service.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Bounded list of internal property IDs to compare side by side. */
@Data
public class PropertyCompareRequest {
    /** Smallest number of properties that makes a comparison. */
    public static final int MIN_PROPERTIES = 2;
    /** Largest number of properties shown side by side. */
    public static final int MAX_PROPERTIES = 5;

    @NotNull
    @Size(min = MIN_PROPERTIES, max = MAX_PROPERTIES)
    @Schema(description = "Internal property IDs (not folios), " + MIN_PROPERTIES + " to " + MAX_PROPERTIES
        + ", no duplicates. Properties are returned in this order.", example = "[12, 34, 56]")
    private List<@NotNull @Positive Long> propertyIds;
}
