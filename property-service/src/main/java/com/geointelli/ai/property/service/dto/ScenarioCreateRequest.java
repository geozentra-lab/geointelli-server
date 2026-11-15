package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.geointelli.ai.property.service.entity.ScenarioType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/scenarios} (SCRUM-147): a named set of user-supplied assumptions. */
public record ScenarioCreateRequest(
        @Schema(description = "Name chosen by the user", example = "Miami duplex - conservative")
        @NotBlank @Size(max = 120)
        String name,

        @Schema(description = "Scenario type; only INVESTMENT is supported", example = "INVESTMENT")
        @NotNull
        ScenarioType scenarioType,

        @Schema(description = "Optional property the scenario is about; only linked, its facts are never changed", example = "42")
        @Positive
        Long propertyId,

        @Schema(description = "User-supplied assumptions as a JSON object, for INVESTMENT the body of "
                + "POST /api/calculations/investment; stored as sent, no result is stored")
        @NotNull
        JsonNode assumptions
) {
}
