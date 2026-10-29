package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.MapLayerDTO;

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

}
