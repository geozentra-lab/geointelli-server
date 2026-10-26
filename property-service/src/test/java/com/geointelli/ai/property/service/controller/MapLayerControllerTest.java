package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.MapLayerDTO;
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
}
