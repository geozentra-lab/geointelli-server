package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.GeometryDTO;
import com.geointelli.ai.property.service.dto.MapLayerDTO;
import com.geointelli.ai.property.service.dto.MapLayerFeaturesDTO;
import com.geointelli.ai.property.service.entity.Parcel;
import com.geointelli.ai.property.service.enums.MapLayer;
import com.geointelli.ai.property.service.exception.MapLayerNotFoundException;
import com.geointelli.ai.property.service.mapper.GeometryMapper;
import com.geointelli.ai.property.service.repository.ParcelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class MapLayerServiceImplTest {

    @Mock
    ParcelRepository parcelRepository;

    @Mock
    GeometryMapper geometryMapper;

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

    @Nested
    @DisplayName("getLayerFeatures Tests")
    class GetLayerFeaturesTests {

        private static final String BBOX = "-80.3,25.7,-80.1,25.9";

        private Parcel parcel(long id) {
            Parcel parcel = new Parcel();
            parcel.setId(id);
            parcel.setFolio("folio-" + id);
            return parcel;
        }

        @Test
        void whenParcelsInBbox_thenReturnFeatures() {
            //Arrange
            GeometryDTO geometry = new GeometryDTO("MultiPolygon", List.of());
            when(parcelRepository.findFeaturesWithinBoundingBox(-80.3, 25.7, -80.1, 25.9, 501))
                    .thenReturn(List.of(parcel(1L), parcel(2L)));
            when(geometryMapper.toDTO(any())).thenReturn(geometry);

            //Act
            MapLayerFeaturesDTO result = mapLayerService.getLayerFeatures("parcels", BBOX);

            //Assert
            assertEquals(MapLayer.PARCELS.getId(), result.layerId());
            assertEquals(500, result.limit());
            assertEquals(2, result.count());
            assertFalse(result.truncated());
            assertEquals(1L, result.features().get(0).id());
            assertEquals("folio-1", result.features().get(0).folio());
            assertEquals(geometry, result.features().get(0).geometry());
        }

        @Test
        void whenMoreParcelsThanLimit_thenTruncateTo500() {
            //Arrange
            List<Parcel> parcels = LongStream.rangeClosed(1, 501).mapToObj(this::parcel).toList();
            when(parcelRepository.findFeaturesWithinBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                    .thenReturn(parcels);

            //Act
            MapLayerFeaturesDTO result = mapLayerService.getLayerFeatures("parcels", BBOX);

            //Assert
            assertEquals(500, result.count());
            assertEquals(500, result.features().size());
            assertTrue(result.truncated());
        }

        @Test
        void whenNoParcelsInBbox_thenReturnEmptyFeatures() {
            //Arrange
            when(parcelRepository.findFeaturesWithinBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                    .thenReturn(List.of());

            //Act
            MapLayerFeaturesDTO result = mapLayerService.getLayerFeatures("parcels", BBOX);

            //Assert
            assertEquals(0, result.count());
            assertTrue(result.features().isEmpty());
            assertFalse(result.truncated());
        }

        @Test
        void whenBboxHasSpaces_thenAccept() {
            //Act
            mapLayerService.getLayerFeatures("parcels", "-80.3, 25.7, -80.1, 25.9");

            //Assert
            verify(parcelRepository).findFeaturesWithinBoundingBox(-80.3, 25.7, -80.1, 25.9, 501);
        }

        @ParameterizedTest
        @ValueSource(strings = {"risk", "price", "schools", "unknown", "PARCELS"})
        void whenLayerNotSupported_thenThrowNotFound(String layerId) {
            //Act & Assert
            MapLayerNotFoundException ex = assertThrows(MapLayerNotFoundException.class,
                    () -> mapLayerService.getLayerFeatures(layerId, BBOX));
            assertEquals("Map layer not supported: " + layerId, ex.getMessage());
            verifyNoInteractions(parcelRepository);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "1,2,3",                     // 3 values
                "1,2,3,4,5",                 // 5 values
                "abc,25.7,-80.1,25.9",       // not a number
                ",25.7,-80.1,25.9",          // empty value
                "NaN,25.7,-80.1,25.9",       // NaN
                "-Infinity,25.7,-80.1,25.9", // infinity
                "-200,25.7,-80.1,25.9",      // longitude < -180
                "-80.3,25.7,200,25.9",       // longitude > 180
                "-80.3,-95,-80.1,25.9",      // latitude < -90
                "-80.3,25.7,-80.1,95",       // latitude > 90
                "-80.1,25.7,-80.3,25.9",     // minLng > maxLng
                "-80.3,25.9,-80.1,25.7",     // minLat > maxLat
                "-80.3,25.7,-80.3,25.9"      // minLng == maxLng
        })
        void whenBboxInvalid_thenThrowBadRequest(String bbox) {
            //Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> mapLayerService.getLayerFeatures("parcels", bbox));
            verifyNoInteractions(parcelRepository);
        }
    }

}
