package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.OpportunityRefreshResultDTO;
import com.geointelli.ai.property.service.service.OpportunityScoreRefreshService;
import com.geointelli.ai.property.service.service.OpportunityScoreRefreshService.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminOpportunityScoreController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminOpportunityScoreControllerTest {

    private static final String URL = "/api/admin/opportunity-scores/refresh";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    OpportunityScoreRefreshService refreshService;

    private static OpportunityRefreshResultDTO result(String mode) {
        return new OpportunityRefreshResultDTO(mode, "opportunity-v1", 3, 2, 1, 0,
                Instant.parse("2026-11-15T10:00:00Z"), Instant.parse("2026-11-15T10:00:02Z"), 2000);
    }

    @Test
    @DisplayName("Without a mode, runs an incremental refresh and returns its counts")
    void defaultMode_isIncremental() throws Exception {
        when(refreshService.refresh(Mode.INCREMENTAL)).thenReturn(result("INCREMENTAL"));

        mockMvc.perform(post(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("INCREMENTAL"))
                .andExpect(jsonPath("$.processed").value(3))
                .andExpect(jsonPath("$.scored").value(2))
                .andExpect(jsonPath("$.unavailable").value(1))
                .andExpect(jsonPath("$.durationMillis").value(2000));
        verify(refreshService).refresh(Mode.INCREMENTAL);
    }

    @Test
    @DisplayName("mode=FULL rescores everything")
    void fullMode_isPassed() throws Exception {
        when(refreshService.refresh(Mode.FULL)).thenReturn(result("FULL"));

        mockMvc.perform(post(URL).param("mode", "FULL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("FULL"));
    }

    @Test
    @DisplayName("An unknown mode is 400 before any run")
    void unknownMode_returns400() throws Exception {
        mockMvc.perform(post(URL).param("mode", "EVERYTHING"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(refreshService);
    }

    @Test
    @DisplayName("A run already in progress is 409")
    void runInProgress_returns409() throws Exception {
        when(refreshService.refresh(Mode.INCREMENTAL))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "An opportunity score refresh is already running"));

        mockMvc.perform(post(URL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An opportunity score refresh is already running"));
    }
}
