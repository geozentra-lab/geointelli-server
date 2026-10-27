package com.geointelli.ai.property.service.controller;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.dto.PortfolioSummaryDTO;
import com.geointelli.ai.property.service.service.PortfolioSummaryService;
class PortfolioSummaryControllerTest {
    final PortfolioSummaryService service=mock(PortfolioSummaryService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new PortfolioSummaryController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules())).build();
    }
    @Test void serializesDatesAsStringsAndAcceptsNoOptionalParameters() throws Exception {
        LocalDate date=LocalDate.of(2026,10,26);
        when(service.summarize(1L,null,null)).thenReturn(new PortfolioSummaryDTO(1L,"Rentals","AVAILABLE",
            Instant.parse("2026-10-26T10:00:00Z"),date,date,date,null,null,null,List.of()));
        mvc.perform(get("/api/portfolios/1/summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.asOfDate").value("2026-10-26"))
            .andExpect(jsonPath("$.generatedAt").value("2026-10-26T10:00:00Z"));
    }
    @Test void bindsInclusiveDateRange() throws Exception {
        mvc.perform(get("/api/portfolios/1/summary?fromDate=2026-10-01&toDate=2026-10-26")).andExpect(status().isOk());
        verify(service).summarize(1L,LocalDate.of(2026,10,1),LocalDate.of(2026,10,26));
    }
    @Test void malformedDateReturns400WithoutInvokingService() throws Exception {
        mvc.perform(get("/api/portfolios/1/summary?fromDate=not-a-date")).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void invalidIdTypeReturns400() throws Exception {
        mvc.perform(get("/api/portfolios/bad/summary")).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void missingPortfolioReturns404() throws Exception {
        when(service.summarize(1L,null,null)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"Portfolio not found"));
        mvc.perform(get("/api/portfolios/1/summary")).andExpect(status().isNotFound());
    }
    @Test void missingBearerIdentityReturns401() throws Exception {
        when(service.summarize(anyLong(),any(),any())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Bearer token required"));
        mvc.perform(get("/api/portfolios/1/summary")).andExpect(status().isUnauthorized());
    }
    @Test void invalidPeriodReturns400() throws Exception {
        when(service.summarize(anyLong(),any(),any())).thenThrow(new IllegalArgumentException("Invalid period"));
        mvc.perform(get("/api/portfolios/1/summary?fromDate=2026-10-26&toDate=2026-10-01")).andExpect(status().isBadRequest());
    }
    @Test void unavailableSectionsReturn503() throws Exception {
        when(service.summarize(1L,null,null)).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Temporarily unavailable"));
        mvc.perform(get("/api/portfolios/1/summary")).andExpect(status().isServiceUnavailable());
    }
}
