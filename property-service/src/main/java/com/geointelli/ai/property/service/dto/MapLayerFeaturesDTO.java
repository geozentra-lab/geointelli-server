package com.geointelli.ai.property.service.dto;

import java.util.List;

/**
 * Bounded features of a map layer.
 *
 * @param layerId   layer identifier
 * @param limit     maximum number of features returned per request
 * @param count     number of features returned
 * @param truncated {@code true} if more features exist in the area than {@code limit}
 * @param features  the features intersecting the bounding box
 */
public record MapLayerFeaturesDTO(
        String layerId,
        int limit,
        int count,
        boolean truncated,
        List<MapFeatureDTO> features) {
}
