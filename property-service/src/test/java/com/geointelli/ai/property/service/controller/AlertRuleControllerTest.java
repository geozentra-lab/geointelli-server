package com.geointelli.ai.property.service.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import com.geointelli.ai.property.service.advice.AlertRuleExceptionHandler;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.dto.AlertRuleDTO;
import com.geointelli.ai.property.service.dto.AlertRulePageDTO;
import com.geointelli.ai.property.service.dto.AlertRuleRequest;
import com.geointelli.ai.property.service.entity.AlertCadence;
import com.geointelli.ai.property.service.entity.AlertEventType;
import com.geointelli.ai.property.service.service.AlertRuleService;

/** Tests rule JSON contracts and status codes without starting infrastructure. */
class AlertRuleControllerTest {
    private static final String URL = "/api/alerts";
    private final AlertRuleService service = mock(AlertRuleService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = MockMvcBuilders.standaloneSetup(new AlertRuleController(service))
            .setControllerAdvice(new AlertRuleExceptionHandler(), new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    private AlertRuleDTO rule() {
        return new AlertRuleDTO(20L, "New Miami homes", AlertEventType.NEW_MATCH, null, 10L,
            AlertCadence.DAILY, false, true, null, null);
    }

    @Test
    void createsSearchRuleWith201AndLocation() throws Exception {
        when(service.create(any())).thenReturn(rule());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"New Miami homes\",\"eventType\":\"NEW_MATCH\",\"savedSearchId\":10}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", URL + "/20"))
            .andExpect(jsonPath("$.eventType").value("NEW_MATCH")).andExpect(jsonPath("$.savedSearchId").value(10))
            .andExpect(jsonPath("$.paused").value(false)).andExpect(jsonPath("$.effectivePaused").value(true));
    }

    @Test
    void bindsPropertyTargetAndExplicitMonitoringSettings() throws Exception {
        when(service.create(any())).thenReturn(rule());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Price\",\"eventType\":\"LISTING_PRICE_CHANGED\",\"propertyId\":100,"
                + "\"cadence\":\"WEEKLY\",\"paused\":true}")).andExpect(status().isCreated());
        ArgumentCaptor<AlertRuleRequest> request = ArgumentCaptor.forClass(AlertRuleRequest.class);
        verify(service).create(request.capture());
        assertEquals(100L, request.getValue().getPropertyId());
        assertEquals(AlertCadence.WEEKLY, request.getValue().getCadence());
        assertTrue(request.getValue().getPaused());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"name\":\"Valid\"}",
        "{\"name\":\" \",\"eventType\":\"NEW_MATCH\",\"savedSearchId\":10}",
        "{\"name\":\"Valid\",\"eventType\":\"INVALID\",\"propertyId\":100}",
        "{\"name\":\"Valid\",\"eventType\":\"NEW_MATCH\",\"savedSearchId\":-1}",
        "{\"name\":\"Valid\",\"eventType\":\"NEW_MATCH\",\"savedSearchId\":10,\"cadence\":\"HOURLY\"}",
        "{\"name\":\"Valid\",\"eventType\":\"NEW_MATCH\",\"savedSearchId\":10,\"paused\":null}"
    })
    void rejectsInvalidCreationBodies(String body) throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void businessTargetValidationReturns400() throws Exception {
        when(service.create(any())).thenThrow(new IllegalArgumentException("Exactly one target is required"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Valid\",\"eventType\":\"NEW_MATCH\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void listsEmptyDataWithDefaultPagination() throws Exception {
        when(service.list(0, 20)).thenReturn(new AlertRulePageDTO(List.of(), 0, 20, 0, 0));
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        verify(service).list(0, 20);
    }

    @Test
    void bindsExplicitPagination() throws Exception {
        when(service.list(1, 5)).thenReturn(new AlertRulePageDTO(List.of(rule()), 1, 5, 6, 2));
        mvc.perform(get(URL).param("page", "1").param("size", "5")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(20));
        verify(service).list(1, 5);
    }

    @Test
    void getsRuleDetails() throws Exception {
        when(service.get(20L)).thenReturn(rule());
        mvc.perform(get(URL + "/20")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New Miami homes"));
    }

    @Test
    void patchCanPauseWithoutSendingCreationFields() throws Exception {
        when(service.update(eq(20L), any())).thenReturn(rule());
        mvc.perform(patch(URL + "/20").contentType(MediaType.APPLICATION_JSON).content("{\"paused\":true}"))
            .andExpect(status().isOk());
        ArgumentCaptor<AlertRuleRequest> patch = ArgumentCaptor.forClass(AlertRuleRequest.class);
        verify(service).update(eq(20L), patch.capture());
        assertTrue(patch.getValue().hasField("paused"));
        assertFalse(patch.getValue().hasField("eventType"));
        assertFalse(patch.getValue().hasField("savedSearchId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "eventType", "cadence", "paused"})
    void patchCannotNullRequiredFields(String field) throws Exception {
        mvc.perform(patch(URL + "/20").contentType(MediaType.APPLICATION_JSON)
            .content("{\"" + field + "\":null}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsRetargetingWith400() throws Exception {
        when(service.update(eq(20L), any())).thenThrow(new IllegalArgumentException("Target and eventType cannot be changed"));
        mvc.perform(patch(URL + "/20").contentType(MediaType.APPLICATION_JSON).content("{\"savedSearchId\":99}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deletesWithNoContent() throws Exception {
        mvc.perform(delete(URL + "/20")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(20L);
    }

    @Test
    void ownershipAndMissingIdentityKeep404And401() throws Exception {
        when(service.get(20L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert rule not found"));
        mvc.perform(get(URL + "/20")).andExpect(status().isNotFound());
        when(service.list(0, 20)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateAndConcurrentTargetErrorsReturn409WithoutSqlDetails() throws Exception {
        when(service.create(any())).thenThrow(new DataIntegrityViolationException("private SQL"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Valid\",\"eventType\":\"NEW_MATCH\",\"savedSearchId\":10}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("The alert rule already exists or its target changed; refresh and retry"));
    }

    @Test
    void invalidPaginationPathAndJsonReturn400() throws Exception {
        mvc.perform(get(URL + "/invalid")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("page", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
        when(service.list(0, 101)).thenThrow(new IllegalArgumentException("Invalid size"));
        mvc.perform(get(URL).param("size", "101")).andExpect(status().isBadRequest());
    }
}
