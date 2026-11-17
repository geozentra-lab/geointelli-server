package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.AdminCoverageHealthDTO;
import com.geointelli.ai.property.service.service.AdminCoverageHealthService;
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
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminCoverageHealthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AdminCoverageHealthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AdminCoverageHealthService adminCoverageHealthService;

    private static final String URL = "/api/admin/coverage-health";

    @Nested
    @DisplayName("Test Coverage Health Endpoint")
    class CoverageHealthTest {

        @Test
        @DisplayName("When the dashboard is built, then return 200 with observation times and unavailable blocks")
        void whenBuilt_thenReturn200() throws Exception {
            // Arrange
            var quality = new AdminCoverageHealthDTO.Quality(AdminCoverageHealthDTO.AVAILABLE,
                    Instant.parse("2026-11-16T09:58:00Z"), 352L, List.of(), null);
            var coverage = new AdminCoverageHealthDTO.Coverage(AdminCoverageHealthDTO.AVAILABLE, 1000L,
                    List.of(new AdminCoverageHealthDTO.SourceCoverage("SALES", 700, new BigDecimal("70.00"))), null);
            var csv = new AdminCoverageHealthDTO.ProviderHealth("CSV_IMPORT", AdminCoverageHealthDTO.UNAVAILABLE,
                    null, null, null, null, 0, "No finished job is recorded");
            when(adminCoverageHealthService.getDashboard()).thenReturn(new AdminCoverageHealthDTO(
                    Instant.parse("2026-11-16T10:00:00Z"), quality, coverage, List.of(csv), AdminCoverageHealthDTO.NOTE));

            // Act & Assert
            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.observedAt").value("2026-11-16T10:00:00Z"))
                    .andExpect(jsonPath("$.quality.generatedAt").value("2026-11-16T09:58:00Z"))
                    .andExpect(jsonPath("$.quality.totalIssues").value(352))
                    .andExpect(jsonPath("$.coverage.sources[0].source").value("SALES"))
                    .andExpect(jsonPath("$.coverage.sources[0].coveragePercent").value(70.00))
                    .andExpect(jsonPath("$.providers[0].status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.providers[0].reason").value("No finished job is recorded"));
            verify(adminCoverageHealthService).getDashboard();
        }
    }
}
