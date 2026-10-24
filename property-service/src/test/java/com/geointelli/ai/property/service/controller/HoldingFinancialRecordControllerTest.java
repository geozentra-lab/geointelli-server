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
import com.geointelli.ai.property.service.dto.FinancialRecordDTO;
import com.geointelli.ai.property.service.dto.FinancialRecordQuery;
import com.geointelli.ai.property.service.dto.FinancialRecordRequest;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import com.geointelli.ai.property.service.entity.FinancialRecordType;
import com.geointelli.ai.property.service.service.HoldingFinancialRecordService;

/** Checks JSON validation, HTTP contracts and scoped errors without external systems. */
class HoldingFinancialRecordControllerTest {
    private static final String URL = "/api/portfolios/10/holdings/20/financial-records";
    private final HoldingFinancialRecordService service = mock(HoldingFinancialRecordService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = MockMvcBuilders.standaloneSetup(new HoldingFinancialRecordController(service))
            .setControllerAdvice(new PortfolioExceptionHandler(), new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }

    private FinancialRecordDTO record() {
        return new FinancialRecordDTO(30L, 10L, 20L, FinancialRecordCategory.RENT, FinancialRecordType.INCOME,
            new BigDecimal("2500.00"), "USD", LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 1, 31), null, "USER_REPORTED_ACTUAL", null, null);
    }

    @Test
    void returnsSingleRecordDetailsWithIsoDates() throws Exception {
        when(service.get(10L, 20L, 30L)).thenReturn(record());
        mvc.perform(get(URL + "/30")).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(30)).andExpect(jsonPath("$.holdingId").value(20))
            .andExpect(jsonPath("$.category").value("RENT")).andExpect(jsonPath("$.amount").value(2500))
            .andExpect(jsonPath("$.transactionDate").value("2025-01-10"));
        verify(service).get(10L, 20L, 30L);
    }

    @Test
    void unavailableRecordDetailsReturn404() throws Exception {
        when(service.get(10L, 20L, 30L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Financial record not found in this holding"));
        mvc.perform(get(URL + "/30")).andExpect(status().isNotFound());
    }

    @Test
    void detailsWithoutIdentityReturn401() throws Exception {
        when(service.get(10L, 20L, 30L)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get(URL + "/30")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRecordPathTypeReturns400() throws Exception {
        mvc.perform(get(URL + "/invalid")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void createsActualRecordWith201AndLocationHeader() throws Exception {
        when(service.create(eq(10L), eq(20L), any())).thenReturn(record());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
            .content("{\"category\":\"RENT\",\"amount\":2500,\"transactionDate\":\"2025-01-10\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", URL + "/30"))
            .andExpect(jsonPath("$.type").value("INCOME")).andExpect(jsonPath("$.amount").value(2500))
            .andExpect(jsonPath("$.transactionDate").value("2025-01-10"))
            .andExpect(jsonPath("$.periodStart").value("2025-01-01"))
            .andExpect(jsonPath("$.source").value("USER_REPORTED_ACTUAL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"category\":\"RENT\",\"amount\":10}",
        "{\"amount\":10,\"transactionDate\":\"2025-01-10\"}",
        "{\"category\":\"RENT\",\"transactionDate\":\"2025-01-10\"}",
        "{\"category\":\"INVALID\",\"amount\":10,\"transactionDate\":\"2025-01-10\"}",
        "{\"category\":\"RENT\",\"amount\":0,\"transactionDate\":\"2025-01-10\"}",
        "{\"category\":\"RENT\",\"amount\":-10,\"transactionDate\":\"2025-01-10\"}",
        "{\"category\":\"RENT\",\"amount\":10.001,\"transactionDate\":\"2025-01-10\"}",
        "{\"category\":\"RENT\",\"amount\":10,\"transactionDate\":\"2025-01-10\",\"currency\":\"EUR\"}",
        "{\"category\":\"RENT\",\"amount\":10,\"transactionDate\":\"2025-01-10\",\"currency\":null}",
        "{\"category\":\"RENT\",\"amount\":10,\"transactionDate\":\"not-a-date\"}"
    })
    void invalidCreationBodiesReturn400(String body) throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void futureActualDateReturns400() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
            .content("{\"category\":\"RENT\",\"amount\":10,\"transactionDate\":\"" + LocalDate.now().plusDays(1) + "\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void listsWithDefaultsAndKeepsMissingDataEmpty() throws Exception {
        when(service.list(eq(10L), eq(20L), any())).thenReturn(new PortfolioPageDTO<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));
        ArgumentCaptor<FinancialRecordQuery> query = ArgumentCaptor.forClass(FinancialRecordQuery.class);
        verify(service).list(eq(10L), eq(20L), query.capture());
        assertEquals(20, query.getValue().getSize());
        assertEquals(0, query.getValue().getPage());
        assertNull(query.getValue().getFromDate());
    }

    @Test
    void bindsDatesCategoryAndPagination() throws Exception {
        when(service.list(eq(10L), eq(20L), any())).thenReturn(new PortfolioPageDTO<>(List.of(record()), 1, 5, 6, 2));
        mvc.perform(get(URL).param("page", "1").param("size", "5").param("category", "RENT")
            .param("fromDate", "2025-01-01").param("toDate", "2025-01-31"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(30));
        ArgumentCaptor<FinancialRecordQuery> query = ArgumentCaptor.forClass(FinancialRecordQuery.class);
        verify(service).list(eq(10L), eq(20L), query.capture());
        assertEquals(1, query.getValue().getPage());
        assertEquals(5, query.getValue().getSize());
        assertEquals(FinancialRecordCategory.RENT, query.getValue().getCategory());
        assertEquals(LocalDate.of(2025, 1, 1), query.getValue().getFromDate());
        assertEquals(LocalDate.of(2025, 1, 31), query.getValue().getToDate());
    }

    @Test
    void invalidPagingCategoryDatesAndRangesReturn400() throws Exception {
        mvc.perform(get(URL).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("category", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("fromDate", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("fromDate", "2025-02-01").param("toDate", "2025-01-01"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void patchChangesOnlySuppliedFieldsAndCanClearOptionalDescription() throws Exception {
        when(service.update(eq(10L), eq(20L), eq(30L), any())).thenReturn(record());
        mvc.perform(patch(URL + "/30").contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":2400,\"description\":null}")).andExpect(status().isOk());
        ArgumentCaptor<FinancialRecordRequest> request = ArgumentCaptor.forClass(FinancialRecordRequest.class);
        verify(service).update(eq(10L), eq(20L), eq(30L), request.capture());
        assertTrue(request.getValue().hasField("amount"));
        assertTrue(request.getValue().hasField("description"));
        assertNull(request.getValue().getDescription());
        assertFalse(request.getValue().hasField("category"));
        assertFalse(request.getValue().hasField("transactionDate"));
        assertFalse(request.getValue().hasField("currency"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"category", "amount", "currency", "transactionDate"})
    void requiredFieldsCannotBeNullOnPatch(String field) throws Exception {
        mvc.perform(patch(URL + "/30").contentType(MediaType.APPLICATION_JSON)
            .content("{\"" + field + "\":null}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void emptyPatchIsAllowedAsNoOp() throws Exception {
        when(service.update(eq(10L), eq(20L), eq(30L), any())).thenReturn(record());
        mvc.perform(patch(URL + "/30").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }

    @Test
    void deleteReturns204WithoutDeletingParents() throws Exception {
        mvc.perform(delete(URL + "/30")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(10L, 20L, 30L);
    }

    @Test
    void missingIdentityReturns401() throws Exception {
        when(service.list(eq(10L), eq(20L), any())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required"));
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void foreignOrMissingParentReturns404() throws Exception {
        when(service.list(eq(10L), eq(20L), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
        mvc.perform(get(URL)).andExpect(status().isNotFound());
    }

    @Test
    void crossHoldingRecordReturns404OnCorrection() throws Exception {
        when(service.update(eq(10L), eq(20L), eq(30L), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Financial record not found in this holding"));
        mvc.perform(patch(URL + "/30").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2400}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void businessPeriodValidationReturns400() throws Exception {
        when(service.create(eq(10L), eq(20L), any())).thenThrow(new IllegalArgumentException("periodEnd must not precede periodStart"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
            .content("{\"category\":\"RENT\",\"amount\":10,\"transactionDate\":\"2025-01-10\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void databaseConflictsUseScoped409WithoutLeakingSql() throws Exception {
        doThrow(new DataIntegrityViolationException("secret SQL")).when(service).delete(10L, 20L, 30L);
        mvc.perform(delete(URL + "/30")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(
                "The portfolio or holding changed or conflicts with an existing record; refresh and retry"));
    }

    @Test
    void malformedJsonAndPathReturn400() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
        mvc.perform(delete(URL + "/invalid")).andExpect(status().isBadRequest());
    }
}
