package com.geointelli.ai.property.service.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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
import com.geointelli.ai.property.service.dto.CrmActivityDTO;
import com.geointelli.ai.property.service.dto.CrmClientDTO;
import com.geointelli.ai.property.service.dto.CrmClientUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmLeadDTO;
import com.geointelli.ai.property.service.dto.CrmLeadSubmissionDTO;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.entity.CrmActivityType;
import com.geointelli.ai.property.service.entity.CrmSubjectType;
import com.geointelli.ai.property.service.entity.LeadStage;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmClientService;
import com.geointelli.ai.property.service.service.CrmLeadService;

/** Verifies public CRM routes, PATCH presence semantics, response codes and safe errors. */
class CrmControllerTest {
    private final CrmClientService clients = mock(CrmClientService.class);
    private final CrmLeadService leads = mock(CrmLeadService.class);
    private final CrmActivityService activity = mock(CrmActivityService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new CrmClientController(clients), new CrmLeadController(leads),
                new CrmActivityController(activity))
            .setControllerAdvice(new CrmExceptionHandler(), new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    private CrmClientDTO client() {
        return new CrmClientDTO(10L, "Alex", "alex@example.com", null, null,
            LocalDateTime.of(2026, 11, 2, 12, 0), LocalDateTime.of(2026, 11, 2, 12, 0));
    }

    private CrmLeadDTO lead() {
        return new CrmLeadDTO(20L, 10L, null, "Enquiry", "MANUAL", LeadStage.NEW, null, null, null);
    }

    @Test
    void createsClientWithLocationAndIsoDates() throws Exception {
        when(clients.create(any())).thenReturn(client());
        mvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Alex\",\"email\":\"alex@example.com\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/clients/10"))
            .andExpect(jsonPath("$.id").value(10)).andExpect(jsonPath("$.createdAt").value("2026-11-02T12:00:00"));
    }

    @Test
    void patchDistinguishesOmittedFieldsFromExplicitNull() throws Exception {
        when(clients.update(eq(10L), any())).thenReturn(client());
        mvc.perform(patch("/api/clients/10").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":null}"))
            .andExpect(status().isOk());
        verify(clients).update(eq(10L), argThat(request -> request.isNotesProvided()
            && request.getNotes() == null && !request.isNameProvided() && !request.isEmailProvided()));
    }

    @Test
    void leadSubmissionForwardsKeyAndUses201Then200ForReplay() throws Exception {
        when(leads.create(any(), eq("key-1"))).thenReturn(new CrmLeadSubmissionDTO(lead(), true), new CrmLeadSubmissionDTO(lead(), false));
        String body = "{\"clientId\":10,\"title\":\"Enquiry\",\"source\":\"MANUAL\"}";
        mvc.perform(post("/api/leads").header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/leads/20"));
        mvc.perform(post("/api/leads").header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("NEW"));
    }

    @Test
    void missingSubmissionKeyIsReportedAsBadRequestByService() throws Exception {
        when(leads.create(any(), isNull())).thenThrow(new IllegalArgumentException("Idempotency-Key is required"));
        mvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listsHaveBoundedPaginationAndStageBinding() throws Exception {
        when(clients.list(0, 20)).thenReturn(new CrmPageDTO<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/clients")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        when(leads.list(1, 5, LeadStage.CONTACTED)).thenReturn(new CrmPageDTO<>(List.of(lead()), 1, 5, 6, 2));
        mvc.perform(get("/api/leads?page=1&size=5&stage=CONTACTED"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void noteReturnsCreatedAndServerOwnedActivityFields() throws Exception {
        when(activity.addLeadNote(eq(20L), any())).thenReturn(new CrmActivityDTO(30L, 10L,
            CrmSubjectType.LEAD, 20L, CrmActivityType.NOTE_ADDED, "Called client", Instant.parse("2026-11-02T12:00:00Z")));
        mvc.perform(post("/api/leads/20/activities").contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Called client\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.activityType").value("NOTE_ADDED"))
            .andExpect(jsonPath("$.createdAt").value("2026-11-02T12:00:00Z"));
    }

    @Test
    void deleteReturns204AndNoBody() throws Exception {
        mvc.perform(delete("/api/clients/10")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(clients).delete(10L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/clients/abc", "/api/leads?stage=INVALID", "/api/clients?page=abc"})
    void invalidPathOrQueryValuesReturn400(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest());
        verifyNoInteractions(clients, leads, activity);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"stage\":\"BOGUS\"}", "{\"clientId\":8}", "{\"userId\":8}"})
    void invalidPatchOrImmutableFieldsAreRejected(String body) throws Exception {
        mvc.perform(patch("/api/leads/20").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(leads);
    }

    @Test
    void ownerLookupErrorsKeep401And404() throws Exception {
        when(clients.get(10L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        mvc.perform(get("/api/clients/10")).andExpect(status().isNotFound());
        when(activity.list(0, 20)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required"));
        mvc.perform(get("/api/crm/activities")).andExpect(status().isUnauthorized());
    }

    @Test
    void integrityErrorsDoNotExposeContactDataOrSql() throws Exception {
        when(clients.create(any())).thenThrow(new DataIntegrityViolationException("INSERT secret-email@example.com"));
        mvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("The CRM record conflicts with an existing record or changed concurrently; refresh and retry"));
    }
}
