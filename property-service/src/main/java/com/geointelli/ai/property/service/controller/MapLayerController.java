package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.MapLayerDTO;
import com.geointelli.ai.property.service.service.MapLayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
