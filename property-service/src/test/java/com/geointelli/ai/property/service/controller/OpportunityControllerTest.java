package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.OpportunityBrowseRequest;
import com.geointelli.ai.property.service.dto.OpportunityPageDTO;
import com.geointelli.ai.property.service.dto.OpportunityScoreDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.service.OpportunityBrowseService;
import com.geointelli.ai.property.service.service.OpportunityScoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OpportunityController.class)
@AutoConfigureMockMvc(addFilters = false)
class OpportunityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    OpportunityScoringService opportunityScoringService;

    @MockBean
    OpportunityBrowseService opportunityBrowseService;

    private static final OpportunityScoreDTO.Factor GROWTH = new OpportunityScoreDTO.Factor("FORECAST_GROWTH_1Y",
            "One-year forecast growth", "MARKET", 20, "AVAILABLE", new BigDecimal("4.00"), "PERCENT",
            new BigDecimal("70.00"), new BigDecimal("14.00"), "One-year forecast +4% ...");
    private static final OpportunityScoreDTO.Factor RENT = new OpportunityScoreDTO.Factor("RENTAL_YIELD", "Rental yield",
            "RENT", 20, "UNAVAILABLE", null, null, null, null, "Unavailable: no rent estimate. It adds no points.");

    @Nested
    @DisplayName("GET /api/properties/id/{id}/opportunity-score")
    class OpportunityScore {

        @Test
        @DisplayName("Returns the score with its factors, coverage, version and valuation")
        void scoredProperty_returns200() throws Exception {
            when(opportunityScoringService.getScore(95340L)).thenReturn(new OpportunityScoreDTO(95340L,
                    "opportunity-v1", "SCORED", new BigDecimal("35.46"), 100, new BigDecimal("50.00"),
                    List.of(GROWTH, RENT), List.of(),
                    new OpportunityScoreDTO.ValuationBasis(new BigDecimal("300000.00"), "supported", "high", "v17+v18",
                            LocalDate.of(2026, 7, 26), "run-1"),
                    Instant.parse("2026-11-15T10:00:00Z"), List.of("note")));

            mockMvc.perform(get("/api/properties/id/95340/opportunity-score"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SCORED"))
                    .andExpect(jsonPath("$.scoreVersion").value("opportunity-v1"))
                    .andExpect(jsonPath("$.score").value(35.46))
                    .andExpect(jsonPath("$.maxScore").value(100))
                    .andExpect(jsonPath("$.coveragePercent").value(50.00))
                    .andExpect(jsonPath("$.factors[0].code").value("FORECAST_GROWTH_1Y"))
                    .andExpect(jsonPath("$.factors[0].points").value(14.00))
                    .andExpect(jsonPath("$.factors[1].status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.factors[1].points").doesNotExist())
                    .andExpect(jsonPath("$.valuation.dataThrough").value("2026-07-26"))
                    .andExpect(jsonPath("$.scoredAt").value("2026-11-15T10:00:00Z"));
        }

        @Test
        @DisplayName("An unavailable score is a 200 with a null score and reasons")
        void unavailableScore_returns200WithReasons() throws Exception {
            when(opportunityScoringService.getScore(7L)).thenReturn(new OpportunityScoreDTO(7L, "opportunity-v1",
                    "UNAVAILABLE", null, 100, new BigDecimal("0.00"), List.of(RENT),
                    List.of(new OpportunityScoreDTO.Reason("NO_VALUATION", "No ZentraValue valuation is stored.")),
                    null, Instant.parse("2026-11-15T10:00:00Z"), List.of()));

            mockMvc.perform(get("/api/properties/id/7/opportunity-score"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.score").doesNotExist())
                    .andExpect(jsonPath("$.reasons[0].code").value("NO_VALUATION"));
        }

        @Test
        @DisplayName("An unknown property is 404")
        void unknownProperty_returns404() throws Exception {
            when(opportunityScoringService.getScore(999L)).thenThrow(new PropertyNotFoundException("Property not found: 999"));

            mockMvc.perform(get("/api/properties/id/999/opportunity-score"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("A non-positive ID is 400 and a non-numeric ID is 400")
        void invalidId_returns400() throws Exception {
            when(opportunityScoringService.getScore(0L)).thenThrow(new IllegalArgumentException("propertyId must be positive"));

            mockMvc.perform(get("/api/properties/id/0/opportunity-score"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("propertyId must be positive"));
            mockMvc.perform(get("/api/properties/id/abc/opportunity-score"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/opportunities")
    class Browse {

        private OpportunityPageDTO page(String status, List<OpportunityPageDTO.Item> items) {
            return new OpportunityPageDTO("opportunity-v1", status, new OpportunityPageDTO.Filters(null, null, null, null, null),
                    "SCORE_DESC", new PageResponse<>(items, 0, 20, items.size(), items.isEmpty() ? 0 : 1, true, true),
                    List.of("note"));
        }

        @Test
        @DisplayName("Without parameters, asks for page 0 of 20 with no filter and the default order")
        void defaults_arePassed() throws Exception {
            when(opportunityBrowseService.browse(any())).thenReturn(page("OK", List.of(new OpportunityPageDTO.Item(95340L,
                    "0101", "1 Main St", "Miami", "33101", "Miami-Dade", "FL", new BigDecimal("35.46"), 100,
                    new BigDecimal("50.00"), new BigDecimal("300000.00"), new BigDecimal("4.00"), List.of(GROWTH, RENT),
                    "run-1", LocalDate.of(2026, 7, 26), Instant.parse("2026-11-15T10:00:00Z")))));

            mockMvc.perform(get("/api/opportunities"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.results.content[0].propertyId").value(95340))
                    .andExpect(jsonPath("$.results.content[0].factors[1].status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.results.content[0].coveragePercent").value(50.00))
                    .andExpect(jsonPath("$.results.totalElements").value(1));

            ArgumentCaptor<OpportunityBrowseRequest> request = ArgumentCaptor.forClass(OpportunityBrowseRequest.class);
            verify(opportunityBrowseService).browse(request.capture());
            assertEquals(new OpportunityBrowseRequest(0, 20, null, null, null, null, null, null), request.getValue());
        }

        @Test
        @DisplayName("Passes every filter and the order as sent")
        void filters_arePassed() throws Exception {
            when(opportunityBrowseService.browse(any())).thenReturn(page("NO_MATCHES", List.of()));

            mockMvc.perform(get("/api/opportunities").param("page", "1").param("size", "5").param("countyId", "1")
                            .param("stateId", "10").param("minScore", "30.5").param("minValue", "100000")
                            .param("maxValue", "500000").param("sort", "VALUE_ASC"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NO_MATCHES"))
                    .andExpect(jsonPath("$.results.content").isEmpty());

            ArgumentCaptor<OpportunityBrowseRequest> request = ArgumentCaptor.forClass(OpportunityBrowseRequest.class);
            verify(opportunityBrowseService).browse(request.capture());
            assertEquals(new OpportunityBrowseRequest(1, 5, 1L, 10L, new BigDecimal("30.5"), new BigDecimal("100000"),
                    new BigDecimal("500000"), "VALUE_ASC"), request.getValue());
        }

        @Test
        @DisplayName("A service validation error is 400 with its message")
        void invalidParameter_returns400() throws Exception {
            when(opportunityBrowseService.browse(any())).thenThrow(new IllegalArgumentException("size must be between 1 and 100"));

            mockMvc.perform(get("/api/opportunities").param("size", "500"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
        }

        @Test
        @DisplayName("A parameter of the wrong type is 400 before the service is called")
        void wrongType_returns400() throws Exception {
            mockMvc.perform(get("/api/opportunities").param("minScore", "high"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/opportunities").param("countyId", "miami"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(opportunityBrowseService);
        }
    }
}
