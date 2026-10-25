package com.geointelli.ai.property.service.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.geointelli.ai.property.service.entity.SavedSearchCadence;
import io.swagger.v3.oas.annotations.media.Schema;

/** Exposes the stored query definition; manual replay reflects current property data. */
@Schema(description = "filtersVersion is server-managed. Cadence and paused are settings for future alert evaluation; "
    + "this endpoint does not send notifications. Pause does not disable manual replay.")
public record SavedSearchDTO(Long id, String name, JsonNode filters, int filtersVersion, SavedSearchCadence cadence,
    boolean paused,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime updatedAt) {
}
