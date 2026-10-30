package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.MapLayerDTO;
import com.geointelli.ai.property.service.dto.MapLayerFeaturesDTO;
import com.geointelli.ai.property.service.service.MapLayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class MapLayerController {

    private final MapLayerService mapLayerService;

    /**
     * Lists the map layers supported by the platform.
     *
     * @return 200 with the available layers and their source, coverage and freshness
     */
    @GetMapping("/layers")
    public ResponseEntity<List<MapLayerDTO>> getMapLayers() {
        List<MapLayerDTO> mapLayers = mapLayerService.getAvailableLayers();
        return ResponseEntity.ok(mapLayers);
    }

    /**
     * Returns the features of a supported map layer within a bounding box.
     *
     * @param layerId catalog layer identifier, e.g. {@code parcels}
     * @param bbox    bounding box as {@code minLng,minLat,maxLng,maxLat}
     * @return 200 with at most 500 features; 400 if the bbox is missing or invalid;
     *         404 if the layer is unknown or not supported
     */
    @GetMapping("/layers/{layerId}/features")
    public ResponseEntity<MapLayerFeaturesDTO> getLayerFeatures(@PathVariable String layerId, @RequestParam String bbox) {
        MapLayerFeaturesDTO layerFeatures = mapLayerService.getLayerFeatures(layerId, bbox);
        return ResponseEntity.ok(layerFeatures);
    }
}
