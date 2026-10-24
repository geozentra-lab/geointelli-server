package com.geointelli.ai.property.service.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.advice.PortfolioExceptionHandler;
import com.geointelli.ai.property.service.dto.HoldingFinancingDTO;
import com.geointelli.ai.property.service.dto.HoldingOccupancyDTO;
import com.geointelli.ai.property.service.dto.PortfolioDTO;
import com.geointelli.ai.property.service.dto.PortfolioHoldingDTO;
import com.geointelli.ai.property.service.dto.PortfolioHoldingRequest;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;
import com.geointelli.ai.property.service.dto.PortfolioRequest;
import com.geointelli.ai.property.service.service.PortfolioHoldingService;
import com.geointelli.ai.property.service.service.PortfolioService;

/** Checks HTTP contracts and bean validation without starting Spring, Keycloak, AWS or a database. */
class PortfolioControllerTest {
    private final PortfolioService portfolios = mock(PortfolioService.class);
    private final PortfolioHoldingService holdings = mock(PortfolioHoldingService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = MockMvcBuilders.standaloneSetup(new PortfolioController(portfolios), new PortfolioHoldingController(holdings))
            .setControllerAdvice(new PortfolioExceptionHandler(), new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    private PortfolioDTO portfolio() {
        return new PortfolioDTO(10L, "Florida rentals", null, "USD", 0, null, null);
    }

    private PortfolioHoldingDTO holding() {
        return new PortfolioHoldingDTO(20L, 10L, 100L, "folio", 31L, "USD",
            LocalDate.of(2025, 1, 2), new BigDecimal("250000"), null, new BigDecimal("50"),
            new HoldingFinancingDTO(), new HoldingOccupancyDTO(), null, null);
    }

    @Test
    void createsPortfolioWithLocationHeader() throws Exception {
        when(portfolios.create(any())).thenReturn(portfolio());
        mvc.perform(post("/api/portfolios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Florida rentals\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/portfolios/10"))
            .andExpect(jsonPath("$.currency").value("USD"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}"})
    void creationRequiresNonblankName(String body) throws Exception {
        mvc.perform(post("/api/portfolios").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(portfolios);
    }

    @Test
    void getsPortfolioAndPagedEmptyList() throws Exception {
        when(portfolios.get(10L)).thenReturn(portfolio());
        when(portfolios.list(0, 20)).thenReturn(new PortfolioPageDTO<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/portfolios/10")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10));
        mvc.perform(get("/api/portfolios")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void bindsExplicitPagination() throws Exception {
        when(portfolios.list(2, 5)).thenReturn(new PortfolioPageDTO<>(List.of(), 2, 5, 0, 0));
        mvc.perform(get("/api/portfolios").param("page", "2").param("size", "5"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2));
        verify(portfolios).list(2, 5);
    }

    @Test
    void patchDistinguishesOmittedNameFromNullDescription() throws Exception {
        when(portfolios.update(eq(10L), any())).thenReturn(portfolio());
        mvc.perform(patch("/api/portfolios/10").contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":null}")).andExpect(status().isOk());
        ArgumentCaptor<PortfolioRequest> request = ArgumentCaptor.forClass(PortfolioRequest.class);
        verify(portfolios).update(eq(10L), request.capture());
        assertFalse(request.getValue().isNameProvided());
        assertTrue(request.getValue().isDescriptionProvided());
        assertNull(request.getValue().getDescription());
    }

    @Test
    void cannotPatchNameToNull() throws Exception {
        mvc.perform(patch("/api/portfolios/10").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":null}")).andExpect(status().isBadRequest());
        verifyNoInteractions(portfolios);
    }

    @Test
    void deletesPortfolioWithNoContent() throws Exception {
        mvc.perform(delete("/api/portfolios/10")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(portfolios).delete(10L);
    }

    @Test
    void returns404ForForeignOrMissingPortfolio() throws Exception {
        when(portfolios.get(10L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
        mvc.perform(get("/api/portfolios/10")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Portfolio not found"));
    }

    @Test
    void missingIdentityReturns401() throws Exception {
        when(portfolios.list(0, 20)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required"));
        mvc.perform(get("/api/portfolios")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsHoldingWithLocationAndIsoDate() throws Exception {
        when(holdings.create(eq(10L), any())).thenReturn(holding());
        mvc.perform(post("/api/portfolios/10/holdings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"propertyId\":100,\"ownershipSharePercent\":50}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/portfolios/10/holdings/20"))
            .andExpect(jsonPath("$.propertyId").value(100)).andExpect(jsonPath("$.acquisitionDate").value("2025-01-02"))
            .andExpect(jsonPath("$.purchasePrice").value(250000))
            .andExpect(jsonPath("$.financing.monthlyPayment").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"propertyId\":null}", "{\"propertyId\":0}",
        "{\"propertyId\":100,\"ownershipSharePercent\":0}",
        "{\"propertyId\":100,\"financing\":{\"loanTermMonths\":0}}",
        "{\"propertyId\":100,\"occupancy\":{\"monthlyRent\":-1}}",
        "{\"propertyId\":100,\"financing\":{\"type\":\"INVALID\"}}",
        "{\"propertyId\":100,\"purchasePrice\":12.345}"})
    void rejectsInvalidHoldingCreationBodies(String body) throws Exception {
        mvc.perform(post("/api/portfolios/10/holdings").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(holdings);
    }

    @Test
    void listsAndGetsHoldingsUnderRequestedPortfolio() throws Exception {
        when(holdings.list(10L, 0, 20)).thenReturn(new PortfolioPageDTO<>(List.of(holding()), 0, 20, 1, 1));
        when(holdings.get(10L, 20L)).thenReturn(holding());
        mvc.perform(get("/api/portfolios/10/holdings")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].propertyId").value(100));
        mvc.perform(get("/api/portfolios/10/holdings/20")).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(20));
    }

    @Test
    void patchCanClearNestedSectionsWithoutSupplyingPropertyId() throws Exception {
        when(holdings.update(eq(10L), eq(20L), any())).thenReturn(holding());
        mvc.perform(patch("/api/portfolios/10/holdings/20").contentType(MediaType.APPLICATION_JSON)
                .content("{\"financing\":null,\"occupancy\":null}")).andExpect(status().isOk());
        ArgumentCaptor<PortfolioHoldingRequest> request = ArgumentCaptor.forClass(PortfolioHoldingRequest.class);
        verify(holdings).update(eq(10L), eq(20L), request.capture());
        assertTrue(request.getValue().hasField("financing"));
        assertTrue(request.getValue().hasField("occupancy"));
        assertFalse(request.getValue().hasField("propertyId"));
    }

    @Test
    void patchCannotExplicitlyNullOwnershipShare() throws Exception {
        mvc.perform(patch("/api/portfolios/10/holdings/20").contentType(MediaType.APPLICATION_JSON)
            .content("{\"ownershipSharePercent\":null}")).andExpect(status().isBadRequest());
    }

    @Test
    void deletesOnlyRequestedHolding() throws Exception {
        mvc.perform(delete("/api/portfolios/10/holdings/20")).andExpect(status().isNoContent());
        verify(holdings).delete(10L, 20L);
        verifyNoInteractions(portfolios);
    }

    @Test
    void handlesDuplicateConflictWithoutLeakingSql() throws Exception {
        when(holdings.create(eq(10L), any())).thenThrow(new DataIntegrityViolationException("secret SQL"));
        mvc.perform(post("/api/portfolios/10/holdings").contentType(MediaType.APPLICATION_JSON)
            .content("{\"propertyId\":100}")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(
                "The portfolio or holding changed or conflicts with an existing record; refresh and retry"));
    }

    @Test
    void returns404ForHoldingUnderWrongPortfolio() throws Exception {
        when(holdings.get(10L, 99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Holding not found in this portfolio"));
        mvc.perform(get("/api/portfolios/10/holdings/99")).andExpect(status().isNotFound());
    }

    @Test
    void invalidPathAndPagingTypesReturn400() throws Exception {
        mvc.perform(get("/api/portfolios/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/portfolios").param("page", "invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/portfolios").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
    }
}
