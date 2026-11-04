package com.geointelli.ai.property.service.dto;

import com.geointelli.ai.property.service.enums.TransportCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One supported transport category near a property, with the distance to its nearest access point.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransportOptionDTO {

    @Schema(description = "Normalized transport category", example = "BUS")
    private TransportCategory category;

    @Schema(description = "Distance to the nearest access point of this category, in the section distanceUnit",
            example = "150.0")
    private BigDecimal nearestDistance;
}
