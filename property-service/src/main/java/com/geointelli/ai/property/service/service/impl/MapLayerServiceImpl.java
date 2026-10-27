package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.MapFeatureDTO;
import com.geointelli.ai.property.service.dto.MapLayerDTO;
import com.geointelli.ai.property.service.dto.MapLayerFeaturesDTO;
import com.geointelli.ai.property.service.entity.Parcel;
import com.geointelli.ai.property.service.enums.MapLayer;
import com.geointelli.ai.property.service.exception.MapLayerNotFoundException;
import com.geointelli.ai.property.service.mapper.GeometryMapper;
import com.geointelli.ai.property.service.repository.ParcelRepository;
import com.geointelli.ai.property.service.service.MapLayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MapLayerServiceImpl implements MapLayerService {

    private static final int FEATURE_LIMIT = 500;

    private final ParcelRepository parcelRepository;
    private final GeometryMapper geometryMapper;

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

    @Override
    public MapLayerFeaturesDTO getLayerFeatures(String layerId, String bbox) {

        MapLayer layer = MapLayer.fromId(layerId)
                .orElseThrow(() -> new MapLayerNotFoundException("Map layer not supported: " + layerId));

        double[] coordinates = parseBbox(bbox);

        List<Parcel> parcels = parcelRepository.findFeaturesWithinBoundingBox(coordinates[0], coordinates[1], coordinates[2], coordinates[3], FEATURE_LIMIT + 1);
        boolean truncated = parcels.size() > FEATURE_LIMIT;
        if (truncated) parcels = parcels.subList(0, FEATURE_LIMIT);

        List<MapFeatureDTO> features = parcels.stream()
                .map(parcel -> new MapFeatureDTO(
                        parcel.getId(),
                        parcel.getFolio(),
                        geometryMapper.toDTO(parcel.getGeom())))
                .toList();

        return new MapLayerFeaturesDTO(
                layer.getId(),
                FEATURE_LIMIT,
                features.size(),
                truncated,
                features
        );
    }

    private double[] parseBbox(String bbox) {
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("bbox must have 4 values: minLng,minLat,maxLng,maxLat");
        }

        double[] coordinates = new double[4];
        for (int i = 0; i < 4; i++) {
            try {
                coordinates[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bbox values must be numbers");
            }
            if (!Double.isFinite(coordinates[i])) {
                throw new IllegalArgumentException("bbox values must be numbers");
            }
        }

        double minLng = coordinates[0];
        double minLat = coordinates[1];
        double maxLng = coordinates[2];
        double maxLat = coordinates[3];

        if (minLng < -180 || maxLng > 180 || minLat < -90 || maxLat > 90) {
            throw new IllegalArgumentException("bbox longitudes must be between -180 and 180 and latitudes between -90 and 90");
        }
        if (minLng >= maxLng || minLat >= maxLat) {
            throw new IllegalArgumentException("bbox must be ordered as minLng,minLat,maxLng,maxLat");
        }

        return coordinates;
    }
}
