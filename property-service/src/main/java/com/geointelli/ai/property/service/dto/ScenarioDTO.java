package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.geointelli.ai.property.service.entity.ScenarioType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** A saved scenario as returned by the API (SCRUM-147): the stored assumptions only, no calculation result. */
public record ScenarioDTO(
        @Schema(description = "Scenario ID", example = "7")
        Long id,
        @Schema(description = "Name chosen by the user", example = "Miami duplex - conservative")
        String name,
        @Schema(description = "Scenario type", example = "INVESTMENT")
        ScenarioType scenarioType,
        @Schema(description = "Version of the assumptions format; server-managed", example = "1")
        int inputVersion,
        @Schema(description = "Linked property, or null", example = "42")
        Long propertyId,
        @Schema(description = "User-supplied assumptions, as stored")
        JsonNode assumptions,
        @Schema(description = "Creation time")
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt,
        @Schema(description = "Last change time")
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime updatedAt
) {
}
