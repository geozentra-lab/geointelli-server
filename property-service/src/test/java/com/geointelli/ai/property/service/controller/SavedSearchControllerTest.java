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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.advice.SavedSearchExceptionHandler;
import com.geointelli.ai.property.service.dto.PropertySearchResponse;
import com.geointelli.ai.property.service.dto.SavedSearchDTO;
import com.geointelli.ai.property.service.dto.SavedSearchPageDTO;
import com.geointelli.ai.property.service.dto.SavedSearchRequest;
import com.geointelli.ai.property.service.entity.SavedSearchCadence;
import com.geointelli.ai.property.service.service.SavedSearchService;

/** Checks HTTP contracts, request field validation and owner-scoped errors. */
class SavedSearchControllerTest {
    private static final String URL = "/api/saved-searches";
    private final SavedSearchService service = mock(SavedSearchService.class);
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new SavedSearchController(service))
            .setControllerAdvice(new SavedSearchExceptionHandler(), new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    private SavedSearchDTO saved() {
        return new SavedSearchDTO(10L, "Miami homes", json.createObjectNode().put("countyId", 31),
            1, SavedSearchCadence.DAILY, false, null, null);
    }

    @Test
    void createsDefinitionWith201AndLocation() throws Exception {
        when(service.create(any())).thenReturn(saved());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Miami homes\",\"filters\":{\"countyId\":31,\"minBedrooms\":3},\"cadence\":\"DAILY\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", URL + "/10"))
            .andExpect(jsonPath("$.filtersVersion").value(1)).andExpect(jsonPath("$.filters.countyId").value(31));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"name\":\"Homes\"}", "{\"filters\":{}}",
        "{\"name\":\"  \",\"filters\":{}}", "{\"name\":\"Homes\",\"filters\":null}",
        "{\"name\":\"Homes\",\"filters\":{},\"paused\":null}",
        "{\"name\":\"Homes\",\"filters\":{},\"cadence\":\"HOURLY\"}",
        "{\"name\":\"Homes\",\"filters\":{\"countyId\":-1}}",
        "{\"name\":\"Homes\",\"filters\":{\"unknownFilter\":true}}"
    })
    void rejectsInvalidCreationBodies(String body) throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void emptyFiltersCanBeSavedAsAnAllPropertiesDefinition() throws Exception {
        when(service.create(any())).thenReturn(saved());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"All homes\",\"filters\":{}}"))
            .andExpect(status().isCreated());
    }

    @Test
    void ignoresPaginationInsideFilters() throws Exception {
        when(service.create(any())).thenReturn(saved());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Homes\",\"filters\":{\"page\":4,\"size\":2,\"countyId\":31}}"))
            .andExpect(status().isCreated());
        ArgumentCaptor<SavedSearchRequest> request = ArgumentCaptor.forClass(SavedSearchRequest.class);
        verify(service).create(request.capture());
        assertEquals(0, request.getValue().getFilters().getPage());
        assertEquals(20, request.getValue().getFilters().getSize());
    }

    @Test
    void listsWithDefaultPaginationAndKeepsEmptyDataEmpty() throws Exception {
        when(service.list(0, 20)).thenReturn(new SavedSearchPageDTO(List.of(), 0, 20, 0, 0));
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        verify(service).list(0, 20);
    }

    @Test
    void bindsExplicitListPagination() throws Exception {
        when(service.list(1, 5)).thenReturn(new SavedSearchPageDTO(List.of(), 1, 5, 0, 0));
        mvc.perform(get(URL).param("page", "1").param("size", "5")).andExpect(status().isOk());
        verify(service).list(1, 5);
    }

    @Test
    void getsOwnedDefinition() throws Exception {
        when(service.get(10L)).thenReturn(saved());
        mvc.perform(get(URL + "/10")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Miami homes"));
    }

    @Test
    void patchAllowsPauseWithoutRequiringNameOrFilters() throws Exception {
        when(service.update(eq(10L), any())).thenReturn(saved());
        mvc.perform(patch(URL + "/10").contentType(MediaType.APPLICATION_JSON).content("{\"paused\":true}"))
            .andExpect(status().isOk());
        ArgumentCaptor<SavedSearchRequest> request = ArgumentCaptor.forClass(SavedSearchRequest.class);
        verify(service).update(eq(10L), request.capture());
        assertTrue(request.getValue().getPaused());
        assertFalse(request.getValue().isNameProvided());
        assertFalse(request.getValue().isFiltersProvided());
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "filters", "cadence", "paused"})
    void patchCannotNullRequiredFields(String field) throws Exception {
        mvc.perform(patch(URL + "/10").contentType(MediaType.APPLICATION_JSON)
            .content("{\"" + field + "\":null}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete(URL + "/10")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(10L);
    }

    @Test
    void replayReturnsTheNormalPropertySearchResponse() throws Exception {
        when(service.results(10L, 0, 20)).thenReturn(new PropertySearchResponse(List.of(), 0, 20, 0, 0, true, true));
        mvc.perform(get(URL + "/10/results")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(0));
        verify(service).results(10L, 0, 20);
    }

    @Test
    void replayBindsFreshPagination() throws Exception {
        when(service.results(10L, 2, 5)).thenReturn(new PropertySearchResponse(List.of(), 2, 5, 0, 0, false, true));
        mvc.perform(get(URL + "/10/results").param("page", "2").param("size", "5")).andExpect(status().isOk());
        verify(service).results(10L, 2, 5);
    }

    @Test
    void ownerAndVersionErrorsKeepTheirStatusCodes() throws Exception {
        when(service.get(10L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found"));
        mvc.perform(get(URL + "/10")).andExpect(status().isNotFound());
        when(service.list(0, 20)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        when(service.results(10L, 0, 20)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Unsupported saved filter version"));
        mvc.perform(get(URL + "/10/results")).andExpect(status().isConflict());
    }

    @Test
    void invalidRangesAndPageSizesFromServiceReturn400() throws Exception {
        when(service.create(any())).thenThrow(new IllegalArgumentException("Invalid bedrooms range"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Homes\",\"filters\":{\"minBedrooms\":4,\"maxBedrooms\":3}}"))
            .andExpect(status().isBadRequest());
        when(service.results(10L, 0, 101)).thenThrow(new IllegalArgumentException("Invalid size"));
        mvc.perform(get(URL + "/10/results").param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonAndInvalidPathTypesReturn400() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/invalid")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("page", "invalid")).andExpect(status().isBadRequest());
    }
}
