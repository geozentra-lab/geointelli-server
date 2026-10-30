package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.MapLayerDTO;
import com.geointelli.ai.property.service.dto.MapLayerFeaturesDTO;

import java.util.List;

public interface MapLayerService {

    /**
     * Returns the map layers that are supported by the platform.
     * <p>
     * Only layers backed by verified geometry data are returned. Unsupported
     * layers (e.g. risk or price) are never listed. When a layer has no data,
     * its coverage is empty, its feature count is {@code 0} and its
     * last update time is {@code null}.
     *
     * @return the list of available map layers with their metadata
     */
    List<MapLayerDTO> getAvailableLayers();

    /**
     * Returns the features of a supported map layer that intersect a bounding box.
     * <p>
     * At most 500 features are returned; {@code truncated} is {@code true}
     * when more features exist in the area. An area without data returns
     * an empty feature list.
     *
     * @param layerId catalog layer identifier, e.g. {@code parcels}
     * @param bbox    bounding box as {@code minLng,minLat,maxLng,maxLat} (WGS84)
     * @return the bounded features of the layer
     * @throws com.geointelli.ai.property.service.exception.MapLayerNotFoundException
     *         if the layer is unknown or not supported
     * @throws IllegalArgumentException if the bounding box is malformed, out of range or not ordered
     */
    MapLayerFeaturesDTO getLayerFeatures(String layerId, String bbox);
}
