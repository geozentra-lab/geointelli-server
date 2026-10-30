package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Map layer returned by the layer catalog.
 *
 * @param id           stable layer identifier, e.g. {@code parcels}
 * @param name         human-readable layer name
 * @param type         geometry type of the layer features, e.g. {@code polygon}
 * @param source       provenance of the layer data
 * @param coverage     names of the counties that have data for this layer; empty if none
 * @param featureCount number of features with geometry; {@code 0} if none
 * @param lastUpdated  latest update time of the layer data; {@code null} if no data exists
 */
public record MapLayerDTO(
        String id,
        String name,
        String type,
        String source,
        List<String> coverage,
        Long featureCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime lastUpdated
) {
}
