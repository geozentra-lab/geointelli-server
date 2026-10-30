package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.GeometryDTO;
import com.geointelli.ai.property.service.dto.MapFeatureDTO;
import com.geointelli.ai.property.service.dto.MapLayerDTO;
import com.geointelli.ai.property.service.dto.MapLayerFeaturesDTO;
import com.geointelli.ai.property.service.exception.MapLayerNotFoundException;
import com.geointelli.ai.property.service.service.MapLayerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MapLayerController.class)
@AutoConfigureMockMvc(addFilters = false)
public class MapLayerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    MapLayerService mapLayerService;

    @Nested
    @DisplayName("getMapLayers")
    class GetMapLayersTest {

        @Test
        void whenLayersExist_thenReturn200WithLayers() throws Exception {
            //Arrange
            MapLayerDTO layer = new MapLayerDTO("parcels", "Property parcels", "polygon", "Geozentra Parcel Data",
                    List.of("Miami-Dade"), 1250L, LocalDateTime.of(2026, 10, 19, 10, 0));
            when(mapLayerService.getAvailableLayers()).thenReturn(List.of(layer));

            //Act & Assert
            mockMvc.perform(get("/api/map/layers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value("parcels"))
                    .andExpect(jsonPath("$[0].name").value("Property parcels"))
                    .andExpect(jsonPath("$[0].type").value("polygon"))
                    .andExpect(jsonPath("$[0].source").value("Geozentra Parcel Data"))
                    .andExpect(jsonPath("$[0].coverage[0]").value("Miami-Dade"))
                    .andExpect(jsonPath("$[0].featureCount").value(1250))
                    .andExpect(jsonPath("$[0].lastUpdated").value("2026-10-19T10:00:00"));
        }

        @Test
        void whenNoParcelData_thenReturn200WithEmptyMetadata() throws Exception {
            //Arrange
            MapLayerDTO layer = new MapLayerDTO("parcels", "Property parcels", "polygon", "Geozentra Parcel Data",
                    List.of(), 0L, null);
            when(mapLayerService.getAvailableLayers()).thenReturn(List.of(layer));

            //Act & Assert
            mockMvc.perform(get("/api/map/layers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("parcels"))
                    .andExpect(jsonPath("$[0].coverage").isEmpty())
                    .andExpect(jsonPath("$[0].featureCount").value(0))
                    .andExpect(jsonPath("$[0].lastUpdated").value(nullValue()));
        }

    }

    @Nested
    @DisplayName("getLayerFeatures Tests")
    class GetLayerFeaturesTests {

        private static final String URL = "/api/map/layers/{layerId}/features";
        private static final String BBOX = "-80.3,25.7,-80.1,25.9";

        @Test
        void whenValidRequest_thenReturn200WithFeatures() throws Exception {
            //Arrange
            MapFeatureDTO feature = new MapFeatureDTO(1L, "0101000000010", new GeometryDTO("MultiPolygon", List.of()));
            when(mapLayerService.getLayerFeatures("parcels", BBOX))
                    .thenReturn(new MapLayerFeaturesDTO("parcels", 500, 1, false, List.of(feature)));

            //Act & Assert
            mockMvc.perform(get(URL, "parcels").param("bbox", BBOX))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.layerId").value("parcels"))
                    .andExpect(jsonPath("$.limit").value(500))
                    .andExpect(jsonPath("$.count").value(1))
                    .andExpect(jsonPath("$.truncated").value(false))
                    .andExpect(jsonPath("$.features[0].id").value(1))
                    .andExpect(jsonPath("$.features[0].folio").value("0101000000010"))
                    .andExpect(jsonPath("$.features[0].geometry.type").value("MultiPolygon"));
        }

        @Test
        void whenNoFeatures_thenReturn200WithEmptyList() throws Exception {
            //Arrange
            when(mapLayerService.getLayerFeatures("parcels", BBOX))
                    .thenReturn(new MapLayerFeaturesDTO("parcels", 500, 0, false, List.of()));

            //Act & Assert
            mockMvc.perform(get(URL, "parcels").param("bbox", BBOX))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(0))
                    .andExpect(jsonPath("$.features").isEmpty());
        }

        @Test
        void whenLayerNotSupported_thenReturn404() throws Exception {
            //Arrange
            when(mapLayerService.getLayerFeatures("risk", BBOX))
                    .thenThrow(new MapLayerNotFoundException("Map layer not supported: risk"));

            //Act & Assert
            mockMvc.perform(get(URL, "risk").param("bbox", BBOX))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Map layer not supported: risk"));
        }

        @Test
        void whenBboxInvalid_thenReturn400() throws Exception {
            //Arrange
            when(mapLayerService.getLayerFeatures("parcels", "1,2,3"))
                    .thenThrow(new IllegalArgumentException("bbox must have 4 values: minLng,minLat,maxLng,maxLat"));

            //Act & Assert
            mockMvc.perform(get(URL, "parcels").param("bbox", "1,2,3"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("bbox must have 4 values: minLng,minLat,maxLng,maxLat"));
        }

        @Test
        void whenBboxMissing_thenReturn400() throws Exception {
            //Act & Assert
            mockMvc.perform(get(URL, "parcels"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("bbox is required"));
        }
    }
}
