package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.MapLayerDTO;
import com.geointelli.ai.property.service.enums.MapLayer;
import com.geointelli.ai.property.service.repository.ParcelRepository;
import com.geointelli.ai.property.service.service.MapLayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MapLayerServiceImpl implements MapLayerService {

    private final ParcelRepository parcelRepository;

    @Override
    public List<MapLayerDTO> getAvailableLayers() {
        MapLayerDTO parcelsLayer = new MapLayerDTO(
                MapLayer.PARCELS.getId(),
                MapLayer.PARCELS.getName(),
                MapLayer.PARCELS.getType(),
                MapLayer.PARCELS.getSource(),
                parcelRepository.findDistinctCountyNames(),
                parcelRepository.countByGeomIsNotNull(),
                parcelRepository.findMaxUpdatedAt());
        return List.of(parcelsLayer);
    }
}
