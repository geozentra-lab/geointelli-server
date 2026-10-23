package com.geointelli.ai.property.service.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionTimedOutException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.advice.ComparableExceptionHandler;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.dto.ComparableCandidateDTO;
import com.geointelli.ai.property.service.dto.ComparableCandidateRequest;
import com.geointelli.ai.property.service.dto.ComparableResponse;
import com.geointelli.ai.property.service.dto.ComparableDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.service.ComparableService;

/** Verifies HTTP binding, validation, serialization and error mapping without external services. */
class ComparableControllerTest {
    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 22);
    private static final String URL = "/api/properties/id/1/comparables";
    private final ComparableService service = mock(ComparableService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = MockMvcBuilders.standaloneSetup(new ComparableController(service))
            .setControllerAdvice(new ComparableExceptionHandler(), new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();
    }

    @Test
    void returnsResultsWithIsoDatesAndBindsDefaultParameters() throws Exception {
        when(service.findComparables(eq(1L), any())).thenReturn(response());
        mvc.perform(get(URL)).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.propertyId").value(1))
            .andExpect(jsonPath("$.status").value("COMPARABLES_FOUND"))
            .andExpect(jsonPath("$.returnedCount").value(3))
            .andExpect(jsonPath("$.asOfDate").value("2026-10-22"))
            .andExpect(jsonPath("$.saleDateFrom").value("2024-10-22"))
            .andExpect(jsonPath("$.comparables[0].candidate.propertyId").value(2))
            .andExpect(jsonPath("$.comparables[0].candidate.saleDate").value("2025-09-30"))
            .andExpect(jsonPath("$.comparables[0].candidate.salePrice").value(690000.0))
            .andExpect(jsonPath("$.comparables[0].rank").value(1))
            .andExpect(jsonPath("$.comparables[0].similarityScore").value(82.22));
        ArgumentCaptor<ComparableCandidateRequest> request = ArgumentCaptor.forClass(ComparableCandidateRequest.class);
        verify(service).findComparables(eq(1L), request.capture());
        assertEquals(24, request.getValue().getMonths());
        assertEquals(2000, request.getValue().getRadiusMeters());
        assertEquals(25, request.getValue().getAreaTolerancePercent());
        assertEquals(1, request.getValue().getBedroomTolerance());
        assertEquals(20, request.getValue().getLimit());
    }

    @Test
    void passesExplicitQueryParametersToTheService() throws Exception {
        when(service.findComparables(eq(1L), any())).thenReturn(response());
        mvc.perform(get(URL).param("months", "12").param("radiusMeters", "3000")
            .param("areaTolerancePercent", "10").param("bedroomTolerance", "0").param("limit", "5"))
            .andExpect(status().isOk());
        ArgumentCaptor<ComparableCandidateRequest> request = ArgumentCaptor.forClass(ComparableCandidateRequest.class);
        verify(service).findComparables(eq(1L), request.capture());
        assertEquals(12, request.getValue().getMonths());
        assertEquals(3000, request.getValue().getRadiusMeters());
        assertEquals(10, request.getValue().getAreaTolerancePercent());
        assertEquals(0, request.getValue().getBedroomTolerance());
        assertEquals(5, request.getValue().getLimit());
    }

    @ParameterizedTest
    @CsvSource({"months,0", "months,121", "months,abc", "radiusMeters,99", "radiusMeters,10001",
        "areaTolerancePercent,-1", "areaTolerancePercent,101", "bedroomTolerance,-1",
        "bedroomTolerance,6", "limit,2", "limit,101"})
    void rejectsInvalidQueryParametersWithoutCallingTheService(String parameter, String value) throws Exception {
        mvc.perform(get(URL).param(parameter, value)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedPropertyIdsWithoutCallingTheService() throws Exception {
        mvc.perform(get(URL.replace("/id/1/", "/id/not-a-number/"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void mapsUnknownPropertyToNotFound() throws Exception {
        when(service.findComparables(eq(1L), any())).thenThrow(new PropertyNotFoundException("Property not found: 1"));
        mvc.perform(get(URL)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void mapsServiceValidationErrorsToBadRequest() throws Exception {
        when(service.findComparables(eq(0L), any())).thenThrow(new IllegalArgumentException("propertyId must be positive"));
        mvc.perform(get(URL.replace("/id/1/", "/id/0/"))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("propertyId must be positive"));
    }

    @Test
    void returnsEmptyResultsAsInsufficientData() throws Exception {
        ComparableResponse empty = response();
        empty.setComparables(List.of());
        empty.setReturnedCount(0);
        empty.setStatus("INSUFFICIENT_DATA");
        when(service.findComparables(eq(1L), any())).thenReturn(empty);
        mvc.perform(get(URL)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INSUFFICIENT_DATA"))
            .andExpect(jsonPath("$.comparables").isEmpty());
    }

    @Test
    void returnsSafeServiceUnavailableForQueryTimeouts() throws Exception {
        when(service.findComparables(eq(1L), any())).thenThrow(new QueryTimeoutException("SELECT private_sql"));
        mvc.perform(get(URL)).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("Comparable retrieval timed out. Please try again later."));
    }

    @Test
    void returnsServiceUnavailableForTransactionTimeouts() throws Exception {
        when(service.findComparables(eq(1L), any())).thenThrow(new TransactionTimedOutException("Expired transaction"));
        mvc.perform(get(URL)).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503));
    }

    private ComparableResponse response() {
        ComparableCandidateDTO first = candidate(2L);
        ComparableDTO ranked = new ComparableDTO();
        ranked.setRank(1);
        ranked.setCandidate(first);
        ranked.setSimilarityScore(new BigDecimal("82.22"));
        ComparableResponse response = new ComparableResponse();
        response.setPropertyId(1L);
        response.setAsOfDate(AS_OF);
        response.setSaleDateFrom(AS_OF.minusMonths(24));
        response.setReturnedCount(3);
        response.setStatus("COMPARABLES_FOUND");
        response.setRankingVersion("comparables-score-v1");
        response.setComparables(List.of(ranked, rankedItem(2, 3L), rankedItem(3, 4L)));
        return response;
    }

    private ComparableCandidateDTO candidate(long id) {
        return ComparableCandidateDTO.builder().propertyId(id).salePrice(new BigDecimal("690000.00"))
            .saleDate(LocalDate.of(2025, 9, 30)).build();
    }

    private ComparableDTO rankedItem(int rank, long id) {
        ComparableDTO item = new ComparableDTO();
        item.setRank(rank);
        item.setCandidate(candidate(id));
        item.setSimilarityScore(BigDecimal.valueOf(80 - rank));
        return item;
    }
}
