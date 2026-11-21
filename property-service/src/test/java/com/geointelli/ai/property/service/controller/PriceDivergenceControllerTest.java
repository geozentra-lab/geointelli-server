package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.PriceDivergenceDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.service.PriceDivergenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PriceDivergenceController.class)
@AutoConfigureMockMvc(addFilters = false)
public class PriceDivergenceControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PriceDivergenceService priceDivergenceService;

    private static final String URL = "/api/properties/id/{id}/price-divergence";

    private static PriceDivergenceDTO response() {
        var subject = new PriceDivergenceDTO.Subject("supported", new BigDecimal("520000.00"), new BigDecimal("1000"),
                new BigDecimal("520.00"), LocalDate.parse("2026-11-10"), "medium");
        var building = new PriceDivergenceDTO.Benchmark("BUILDING", PriceDivergenceDTO.UNAVAILABLE, "900",
                PriceDivergenceDTO.BASIS, 3, null, null, null, null, null, null, null,
                "Only 3 other valued properties in this group (minimum 5)");
        var community = new PriceDivergenceDTO.Benchmark("COMMUNITY", PriceDivergenceDTO.AVAILABLE, "CORAL GARDENS SUB",
                PriceDivergenceDTO.BASIS, 12, new BigDecimal("480000.00"), new BigDecimal("495000.00"),
                new BigDecimal("455.10"), new BigDecimal("40000.00"), new BigDecimal("8.33"),
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-10"), null);
        var drivers = new PriceDivergenceDTO.Drivers(PriceDivergenceDTO.AVAILABLE, PriceDivergenceDTO.DRIVER_SOURCE,
                "COMMUNITY", 12, new BigDecimal("495000.00"), new BigDecimal("25000.00"),
                List.of(new PriceDivergenceDTO.DriverFactor("living_area", "Living area",
                new BigDecimal("60000.00"), new BigDecimal("45000.00"), new BigDecimal("15000.00"))),
                new BigDecimal("15000.00"), null);
        return new PriceDivergenceDTO(1542L, "0141390012345", Instant.parse("2026-11-18T10:00:00Z"), subject,
                List.of(building, community), "COMMUNITY", drivers,
                new PriceDivergenceDTO.Residual(PriceDivergenceDTO.AVAILABLE, "COMMUNITY", new BigDecimal("10000.00"), null),
                new PriceDivergenceDTO.Confidence("MEDIUM", "COMMUNITY benchmark from 12 valued properties"),
                new PriceDivergenceDTO.AskingPriceComparison(PriceDivergenceDTO.UNAVAILABLE, null, null,
                        "No asking-price data is recorded"),
                PriceDivergenceDTO.NOTE);
    }

    @Nested
    @DisplayName("Test Price Divergence Endpoint")
    class PriceDivergenceTest {

        @Test
        @DisplayName("When the property exists, then return 200 with benchmarks, drivers and unavailable values")
        void whenExists_thenReturn200() throws Exception {
            // Arrange
            when(priceDivergenceService.getDivergence(1542L)).thenReturn(response());

            // Act & Assert
            mockMvc.perform(get(URL, 1542))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.observedAt").value("2026-11-18T10:00:00Z"))
                    .andExpect(jsonPath("$.subject.valuationDate").value("2026-11-10"))
                    .andExpect(jsonPath("$.benchmarks[0].status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.benchmarks[0].medianValue").doesNotExist())
                    .andExpect(jsonPath("$.benchmarks[0].reason").value("Only 3 other valued properties in this group (minimum 5)"))
                    .andExpect(jsonPath("$.benchmarks[1].divergencePct").value(8.33))
                    .andExpect(jsonPath("$.benchmarks[1].averageValue").value(495000.00))
                    .andExpect(jsonPath("$.benchmarks[1].valuationsFrom").value("2026-10-01"))
                    .andExpect(jsonPath("$.primaryBenchmark").value("COMMUNITY"))
                    .andExpect(jsonPath("$.drivers.groupAverageValue").value(495000.00))
                    .andExpect(jsonPath("$.drivers.divergenceFromAverageUsd").value(25000.00))
                    .andExpect(jsonPath("$.drivers.factors[0].differenceUsd").value(15000.00))
                    .andExpect(jsonPath("$.drivers.factors[0].pct").doesNotExist())
                    .andExpect(jsonPath("$.unexplainedResidual.usd").value(10000.00))
                    .andExpect(jsonPath("$.askingPriceComparison.status").value("UNAVAILABLE"));
            verify(priceDivergenceService).getDivergence(1542L);
        }

        @Test
        @DisplayName("When the property does not exist, then return 404")
        void whenUnknown_thenReturn404() throws Exception {
            // Arrange
            when(priceDivergenceService.getDivergence(999L)).thenThrow(new PropertyNotFoundException("Property not found: 999"));

            // Act & Assert
            mockMvc.perform(get(URL, 999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Property not found: 999"));
        }

        @Test
        @DisplayName("When the ID is not positive, then return 400")
        void whenNotPositive_thenReturn400() throws Exception {
            // Arrange
            when(priceDivergenceService.getDivergence(0L)).thenThrow(new IllegalArgumentException("propertyId must be positive"));

            // Act & Assert
            mockMvc.perform(get(URL, 0))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("propertyId must be positive"));
        }

        @Test
        @DisplayName("When the ID is not a number, then return 400 without calling the service")
        void whenNotNumber_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(URL, "abc"))
                    .andExpect(status().isBadRequest());
            verify(priceDivergenceService, never()).getDivergence(any());
        }
    }
}
