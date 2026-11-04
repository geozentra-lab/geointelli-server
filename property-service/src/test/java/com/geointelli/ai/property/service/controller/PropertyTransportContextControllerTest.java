package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.TransportContextDTO;
import com.geointelli.ai.property.service.dto.TransportOptionDTO;
import com.geointelli.ai.property.service.enums.TransportCategory;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.service.PropertyTransportContextService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(PropertyTransportContextController.class)
@AutoConfigureMockMvc(addFilters = false)
public class PropertyTransportContextControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PropertyTransportContextService propertyTransportContextService;

    private static TransportContextDTO context(String status, List<TransportOptionDTO> transport, String error) {
        TransportContextDTO context = new TransportContextDTO();
        context.setPropertyId(42L);
        context.setStatus(status);
        context.setDistanceUnit(TransportContextDTO.DISTANCE_UNIT);
        context.setSource(TransportContextDTO.SOURCE);
        context.setTransport(transport);
        context.setError(error);
        return context;
    }

    @Nested
    @DisplayName("Test Transport Context Endpoint")
    class TransportContextTest {

        private static final String URL = "/api/properties/id/{id}/location-intelligence/transport";

        @Test
        @DisplayName("When transport data exists, then return 200 with the AVAILABLE context")
        void whenTransportDataExists_thenReturn200WithAvailableContext() throws Exception {
            // Arrange
            TransportContextDTO available = context(TransportContextDTO.STATUS_AVAILABLE, List.of(
                    new TransportOptionDTO(TransportCategory.RAIL, new BigDecimal("1200")),
                    new TransportOptionDTO(TransportCategory.BUS, new BigDecimal("150"))), null);
            available.setTransportationScore(59);
            available.setTransportationScoreLabel(TransportContextDTO.LABEL_FAIR);
            when(propertyTransportContextService.getTransportContext(42L)).thenReturn(available);

            // Act & Assert
            mockMvc.perform(get(URL, 42))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.propertyId").value(42))
                    .andExpect(jsonPath("$.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.distanceUnit").value("m"))
                    .andExpect(jsonPath("$.transport.length()").value(2))
                    .andExpect(jsonPath("$.transport[0].category").value("RAIL"))
                    .andExpect(jsonPath("$.transport[0].nearestDistance").value(1200))
                    .andExpect(jsonPath("$.transport[1].category").value("BUS"))
                    .andExpect(jsonPath("$.transportationScore").value(59))
                    .andExpect(jsonPath("$.transportationScoreLabel").value("Fair"))
                    .andExpect(jsonPath("$.dataUpdatedAt").doesNotExist())
                    .andExpect(jsonPath("$.source").value(TransportContextDTO.SOURCE));
        }

        @Test
        @DisplayName("When the transport data source failed, then return 200 with the UNAVAILABLE context")
        void whenTransportSourceFailed_thenReturn200WithUnavailableContext() throws Exception {
            // Arrange
            when(propertyTransportContextService.getTransportContext(42L))
                    .thenReturn(context(TransportContextDTO.STATUS_UNAVAILABLE, List.of(),
                            "Transport data source unavailable"));

            // Act & Assert
            mockMvc.perform(get(URL, 42))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.error").value("Transport data source unavailable"))
                    .andExpect(jsonPath("$.transport").isEmpty());
        }

        @Test
        @DisplayName("When the service rejects the ID, then return 400")
        void whenServiceRejectsId_thenReturn400() throws Exception {
            // Arrange
            when(propertyTransportContextService.getTransportContext(0L))
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
            when(propertyTransportContextService.getTransportContext(999L))
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
            verifyNoInteractions(propertyTransportContextService);
        }
    }
}
