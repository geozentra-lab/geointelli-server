package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.SchoolContextDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.service.PropertySchoolContextService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PropertyLocationIntelligenceController.class)
@AutoConfigureMockMvc(addFilters = false)
public class PropertyLocationIntelligenceControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PropertySchoolContextService propertySchoolContextService;

    private static SchoolContextDTO context(String status, Double distPublic, Integer within1km, String note) {
        SchoolContextDTO context = new SchoolContextDTO();
        context.setPropertyId(42L);
        context.setStatus(status);
        context.setDistanceUnit(SchoolContextDTO.DISTANCE_UNIT);
        context.setSource(SchoolContextDTO.SOURCE);
        context.setNearestPublicSchoolDistance(distPublic);
        context.setSchoolsWithin1km(within1km);
        context.setNote(note);
        return context;
    }

    @Nested
    @DisplayName("Test School Context Endpoint")
    class SchoolContextTest {

        private static final String URL = "/api/properties/id/{id}/location-intelligence/schools";

        @Test
        @DisplayName("When school data exists, then return 200 with the AVAILABLE context")
        void whenSchoolDataExists_thenReturn200WithAvailableContext() throws Exception {
            // Arrange
            when(propertySchoolContextService.getSchoolContext(42L))
                    .thenReturn(context(SchoolContextDTO.STATUS_AVAILABLE, 800.0, 3, null));

            // Act & Assert
            mockMvc.perform(get(URL, 42))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.propertyId").value(42))
                    .andExpect(jsonPath("$.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.distanceUnit").value("m"))
                    .andExpect(jsonPath("$.nearestPublicSchoolDistance").value(800.0))
                    .andExpect(jsonPath("$.schoolsWithin1km").value(3))
                    .andExpect(jsonPath("$.source").value(SchoolContextDTO.SOURCE));
        }

        @Test
        @DisplayName("When the property has no school data, then return 200 with the NO_COVERAGE context")
        void whenNoSchoolData_thenReturn200WithNoCoverageContext() throws Exception {
            // Arrange
            when(propertySchoolContextService.getSchoolContext(42L))
                    .thenReturn(context(SchoolContextDTO.STATUS_NO_COVERAGE, null, null,
                            "No school data available for this property"));

            // Act & Assert
            mockMvc.perform(get(URL, 42))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NO_COVERAGE"))
                    .andExpect(jsonPath("$.note").value("No school data available for this property"))
                    .andExpect(jsonPath("$.schoolsWithin1km").doesNotExist());
        }

        @Test
        @DisplayName("When the service rejects the ID, then return 400")
        void whenServiceRejectsId_thenReturn400() throws Exception {
            // Arrange
            when(propertySchoolContextService.getSchoolContext(0L))
                    .thenThrow(new IllegalArgumentException("id must be a positive number"));

            // Act & Assert
            mockMvc.perform(get(URL, 0))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("id must be a positive number"));
        }

        @Test
        @DisplayName("When the property does not exist, then return 404")
        void whenPropertyDoesNotExist_thenReturn404() throws Exception {
            // Arrange
            when(propertySchoolContextService.getSchoolContext(999L))
                    .thenThrow(new PropertyNotFoundException("No property found with id: 999"));

            // Act & Assert
            mockMvc.perform(get(URL, 999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("No property found with id: 999"));
        }

        @Test
        @DisplayName("When the ID is not a number, then return 400 without calling the service")
        void whenIdIsNotANumber_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(URL, "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(propertySchoolContextService);
        }
    }
}
