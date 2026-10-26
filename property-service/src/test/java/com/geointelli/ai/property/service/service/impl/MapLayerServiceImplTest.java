package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.MapLayerDTO;
import com.geointelli.ai.property.service.enums.MapLayer;
import com.geointelli.ai.property.service.repository.ParcelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class MapLayerServiceImplTest {

    @Mock
    ParcelRepository parcelRepository;

    @InjectMocks
    MapLayerServiceImpl mapLayerService;


    @Nested
    @DisplayName("getAvailableLayers Tests")
    class GetAvailableLayersTests {

        @Test
        void whenParcelDataExists_thenReturnParcelsLayerWithMetadata() {
            //Arrange
            when(parcelRepository.findDistinctCountyNames())
                    .thenReturn(List.of("County1", "County2"));
            when(parcelRepository.countByGeomIsNotNull())
                    .thenReturn(100L);
            when(parcelRepository.findMaxUpdatedAt())
                    .thenReturn(LocalDateTime.of(2026, 10, 26, 12, 0));
            //Act
            List<MapLayerDTO> layers = mapLayerService.getAvailableLayers();

            //Assert
            assertEquals(1, layers.size());

            MapLayerDTO layer = layers.get(0);
            assertEquals(MapLayer.PARCELS.getId(), layer.id());
            assertEquals(MapLayer.PARCELS.getName(), layer.name());
            assertEquals(MapLayer.PARCELS.getType(), layer.type());
            assertEquals(MapLayer.PARCELS.getSource(), layer.source());
            assertEquals(List.of("County1", "County2"), layer.coverage());
            assertEquals(100L, layer.featureCount());
            assertEquals(LocalDateTime.of(2026, 10, 26, 12, 0), layer.lastUpdated());
        }


        @Test
        void whenNoParcelData_thenReturnParcelsLayerWithEmptyMetadata() {
            //Arrange
            when(parcelRepository.findDistinctCountyNames())
                    .thenReturn(List.of());
            when(parcelRepository.countByGeomIsNotNull())
                    .thenReturn(0L);
            when(parcelRepository.findMaxUpdatedAt())
                    .thenReturn(null);
            //Act
            List<MapLayerDTO> layers = mapLayerService.getAvailableLayers();

            //Assert
            assertEquals(1, layers.size());

            MapLayerDTO layer = layers.get(0);
            assertEquals(MapLayer.PARCELS.getId(), layer.id());
            assertTrue(layer.coverage().isEmpty());
            assertEquals(0L, layer.featureCount());
            assertNull(layer.lastUpdated());
        }

        @Test
        void whenParcelDataExistsInSeveralCounties_thenCoverageListsAllCounties() {
            //Arrange
            when(parcelRepository.findDistinctCountyNames())
                    .thenReturn(List.of("Broward", "Miami-Dade"));
            when(parcelRepository.countByGeomIsNotNull())
                    .thenReturn(250L);
            when(parcelRepository.findMaxUpdatedAt())
                    .thenReturn(LocalDateTime.of(2026, 10, 26, 12, 0));
            //Act
            List<MapLayerDTO> layers = mapLayerService.getAvailableLayers();

            //Assert
            assertEquals(List.of("Broward", "Miami-Dade"), layers.get(0).coverage());
        }

        @Test
        void whenGettingLayers_thenOnlySupportedLayersAreListed() {
            //Act
            List<MapLayerDTO> layers = mapLayerService.getAvailableLayers();

            //Assert
            List<String> ids = layers.stream().map(MapLayerDTO::id).toList();
            assertEquals(List.of(MapLayer.PARCELS.getId()), ids);
            assertTrue(ids.stream().noneMatch(id -> id.contains("risk") || id.contains("price")));
        }
    }
}
