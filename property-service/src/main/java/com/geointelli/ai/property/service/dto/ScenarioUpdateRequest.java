package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/scenarios/{id}} (SCRUM-147). Every field is optional: a missing (or null) field is
 * left unchanged; {@code assumptions}, when sent, replaces the stored assumptions as a whole.
 */
public record ScenarioUpdateRequest(
        @Schema(description = "New name; must not be blank when sent", example = "Miami duplex - optimistic")
        @Size(max = 120)
        String name,

        @Schema(description = "New linked property", example = "42")
        @Positive
        Long propertyId,

        @Schema(description = "New assumptions as a JSON object; replaces the stored ones as a whole")
        JsonNode assumptions
) {
}
