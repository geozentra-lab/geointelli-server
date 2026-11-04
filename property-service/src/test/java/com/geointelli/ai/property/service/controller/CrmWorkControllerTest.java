package com.geointelli.ai.property.service.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.advice.CrmExceptionHandler;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.CrmPipelineDTO;
import com.geointelli.ai.property.service.dto.CrmTaskDTO;
import com.geointelli.ai.property.service.dto.CrmTaskSummaryDTO;
import com.geointelli.ai.property.service.dto.CrmTransactionDTO;
import com.geointelli.ai.property.service.entity.CrmTaskStatus;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;
import com.geointelli.ai.property.service.service.CrmTaskService;
import com.geointelli.ai.property.service.service.CrmTransactionService;

/** Tests HTTP contracts separately from the database-backed workflow tests. */
class CrmWorkControllerTest {
    private final CrmTaskService tasks = mock(CrmTaskService.class);
    private final CrmTransactionService transactions = mock(CrmTransactionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new CrmTaskController(tasks), new CrmTransactionController(transactions))
            .setControllerAdvice(new CrmExceptionHandler(), new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    private CrmTaskDTO task() {
        return new CrmTaskDTO(1L, "Call", null, CrmTaskStatus.OPEN, Instant.parse("2026-11-04T12:00:00Z"),
            null, false, null, null, null, null, null, null);
    }

    private CrmTransactionDTO deal() {
        return new CrmTransactionDTO(2L, "Deal", null, CrmTransactionStage.PROSPECT, 10L, null, 100L,
            null, null, null, null);
    }

    @Test
    void createsTaskAndNormalizesAnOffsetInstantThroughJsonBinding() throws Exception {
        when(tasks.create(any())).thenReturn(task());
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Call\",\"dueAt\":\"2026-11-04T13:00:00+01:00\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/tasks/1"))
            .andExpect(jsonPath("$.dueAt").value("2026-11-04T12:00:00Z"));
        verify(tasks).create(argThat(body -> body.dueAt().equals(Instant.parse("2026-11-04T12:00:00Z"))));
    }

    @Test
    void createsDealAndBindsStageFilter() throws Exception {
        when(transactions.create(any())).thenReturn(deal());
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Deal\",\"clientId\":10,\"propertyId\":100}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/transactions/2"));
        when(transactions.list(0, 20, CrmTransactionStage.CLOSED)).thenReturn(new CrmPageDTO<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/transactions?stage=CLOSED")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void patchDistinguishesNullAndOmittedFields() throws Exception {
        when(tasks.update(eq(1L), any())).thenReturn(task());
        mvc.perform(patch("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content("{\"dueAt\":null}"))
            .andExpect(status().isOk());
        verify(tasks).update(eq(1L), argThat(body -> body.isDueAtProvided() && body.getDueAt() == null && !body.isStatusProvided()));
        when(transactions.update(eq(2L), any())).thenReturn(deal());
        mvc.perform(patch("/api/transactions/2").contentType(MediaType.APPLICATION_JSON).content("{\"expectedCloseDate\":null}"))
            .andExpect(status().isOk());
        verify(transactions).update(eq(2L), argThat(body -> body.isExpectedCloseDateProvided() && !body.isStageProvided()));
    }

    @Test
    void summaryAndPipelineUseTheirStaticRoutesAndExposeCounts() throws Exception {
        when(tasks.summary()).thenReturn(new CrmTaskSummaryDTO(2, 1, 0, 1, 0, 1, Instant.parse("2026-11-04T12:00:00Z")));
        mvc.perform(get("/api/tasks/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.overdue").value(1));
        when(transactions.pipeline()).thenReturn(new CrmPipelineDTO(1, Map.of(CrmTransactionStage.CLOSED, 1L), Instant.now()));
        mvc.perform(get("/api/transactions/pipeline")).andExpect(status().isOk()).andExpect(jsonPath("$.stages.CLOSED").value(1));
    }

    @Test
    void historyIsPaginatedAndDeleteHasNoBody() throws Exception {
        when(tasks.history(1L, 0, 20)).thenReturn(new CrmPageDTO<>(List.of(), 0, 20, 0, 0));
        when(transactions.history(2L, 1, 5)).thenReturn(new CrmPageDTO<>(List.of(), 1, 5, 0, 0));
        mvc.perform(get("/api/tasks/1/activities")).andExpect(status().isOk());
        mvc.perform(get("/api/transactions/2/activities?page=1&size=5")).andExpect(status().isOk());
        mvc.perform(delete("/api/tasks/1")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(tasks).delete(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"status\":\"BOGUS\"}", "{\"dueAt\":\"tomorrow\"}", "{\"dueAt\":\"2026-11-04T12:00:00\"}", "{\"clientId\":8}", "{\"userId\":8}"})
    void invalidTaskPatchCannotReachService(String body) throws Exception {
        mvc.perform(patch("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(tasks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"stage\":\"BOGUS\"}", "{\"expectedCloseDate\":\"2026-02-30\"}", "{\"clientId\":8}", "{\"closedAt\":\"2026-11-04T12:00:00Z\"}"})
    void invalidDealPatchCannotReachService(String body) throws Exception {
        mvc.perform(patch("/api/transactions/2").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(transactions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/tasks/abc", "/api/tasks?status=BOGUS", "/api/transactions?stage=BOGUS", "/api/tasks?page=abc"})
    void invalidPathsAndFiltersReturn400(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest());
    }

    @Test
    void authOwnershipAndTransitionErrorsKeepTheirStatus() throws Exception {
        when(tasks.get(1L)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required"));
        mvc.perform(get("/api/tasks/1")).andExpect(status().isUnauthorized());
        when(transactions.get(2L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        mvc.perform(get("/api/transactions/2")).andExpect(status().isNotFound());
        when(tasks.update(eq(1L), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Unsupported transition"));
        mvc.perform(patch("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void databaseErrorsAreSafe409Responses() throws Exception {
        when(transactions.create(any())).thenThrow(new DataIntegrityViolationException("secret database SQL"));
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(
                "The CRM record conflicts with an existing record or changed concurrently; refresh and retry"));
    }
}
