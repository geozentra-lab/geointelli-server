package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.PropertySearchRequest;
import com.geointelli.ai.property.service.dto.PropertySearchResponse;
import com.geointelli.ai.property.service.dto.SavedSearchDTO;
import com.geointelli.ai.property.service.dto.SavedSearchFilters;
import com.geointelli.ai.property.service.dto.SavedSearchRequest;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.entity.SavedSearchCadence;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.SavedSearchRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PropertySearchService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/** Verifies canonical definitions, owner isolation, partial changes and replay through the existing search service. */
class SavedSearchServiceImplTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final SavedSearchRepository searches = mock(SavedSearchRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final PropertySearchService propertySearch = mock(PropertySearchService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final SavedSearchServiceImpl service = new SavedSearchServiceImpl(searches, currentUser, propertySearch,
        new PropertySearchValidationServiceImpl(FACTORY.getValidator()), json, FACTORY.getValidator());
    private SavedSearch saved;

    @AfterAll
    static void cleanup() { FACTORY.close(); }

    @BeforeEach
    void setup() throws Exception {
        User user = new User();
        user.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
        saved = new SavedSearch();
        saved.setId(10L);
        saved.setUser(user);
        saved.setName("Original");
        saved.setFilters(json.readTree("{\"countyId\":31,\"minBedrooms\":3,\"sortBy\":\"bedrooms\",\"direction\":\"desc\"}"));
        when(searches.findByIdAndUser_Id(10L, 7L)).thenReturn(Optional.of(saved));
        when(searches.findOwnedForUpdate(10L, 7L)).thenReturn(Optional.of(saved));
        when(searches.saveAndFlush(any())).thenAnswer(call -> {
            SavedSearch value = call.getArgument(0);
            if (value.getId() == null) value.setId(11L);
            return value;
        });
    }

    private SavedSearchRequest request() {
        SavedSearchRequest request = new SavedSearchRequest();
        request.setName("  Miami homes  ");
        SavedSearchFilters filters = new SavedSearchFilters();
        filters.setCountyId(31L);
        filters.setMinBedrooms(new BigDecimal("3"));
        filters.setCity("  MIAMI  ");
        request.setFilters(filters);
        return request;
    }

    @Test
    void savesCanonicalOwnedFiltersWithoutExecutingAQuery() {
        SavedSearchDTO result = service.create(request());
        assertEquals("Miami homes", result.name());
        assertEquals("miami", result.filters().get("city").asText());
        assertEquals(31L, result.filters().get("countyId").asLong());
        assertEquals("id", result.filters().get("sortBy").asText());
        assertEquals("asc", result.filters().get("direction").asText());
        assertFalse(result.filters().has("state"));
        assertFalse(result.filters().has("page"));
        assertFalse(result.filters().has("size"));
        assertEquals(1, result.filtersVersion());
        assertEquals(SavedSearchCadence.DAILY, result.cadence());
        assertFalse(result.paused());
        verifyNoInteractions(propertySearch);
    }

    @Test
    void allowsExplicitMonitoringSettings() {
        SavedSearchRequest request = request();
        request.setCadence(SavedSearchCadence.WEEKLY);
        request.setPaused(true);
        SavedSearchDTO result = service.create(request);
        assertTrue(result.paused());
        assertEquals(SavedSearchCadence.WEEKLY, result.cadence());
    }

    @Test
    void rejectsInvalidFiltersWithoutSavingOrSearching() {
        SavedSearchRequest request = request();
        request.getFilters().setMaxBedrooms(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(searches, never()).saveAndFlush(any());
        verifyNoInteractions(propertySearch);
    }

    @Test
    void requiresNameAndFiltersOnCreate() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new SavedSearchRequest()));
        SavedSearchRequest request = new SavedSearchRequest();
        request.setName("Valid");
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void rejectsBlankOrOverlongNames() {
        SavedSearchRequest request = request();
        request.setName("  ");
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        request.setName("x".repeat(121));
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void metadataPatchRetainsOriginalFiltersAndCadence() {
        SavedSearchRequest patch = new SavedSearchRequest();
        patch.setName(" Renamed ");
        SavedSearchDTO result = service.update(10L, patch);
        assertEquals("Renamed", result.name());
        assertEquals(31, result.filters().get("countyId").asInt());
        assertEquals(SavedSearchCadence.DAILY, result.cadence());
        verify(searches).findOwnedForUpdate(10L, 7L);
    }

    @Test
    void filtersPatchReplacesRatherThanMerges() {
        SavedSearchRequest patch = new SavedSearchRequest();
        SavedSearchFilters filters = new SavedSearchFilters();
        filters.setZip("33101");
        patch.setFilters(filters);
        SavedSearchDTO result = service.update(10L, patch);
        assertFalse(result.filters().has("countyId"));
        assertEquals("33101", result.filters().get("zip").asText());
    }

    @Test
    void invalidFilterPatchDoesNotMutateNameOrStoredDefinition() {
        SavedSearchRequest patch = request();
        patch.getFilters().setSortBy("invalid");
        assertThrows(IllegalArgumentException.class, () -> service.update(10L, patch));
        assertEquals("Original", saved.getName());
        assertEquals(31, saved.getFilters().get("countyId").asInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "filters", "cadence", "paused"})
    void explicitNullCannotClearRequiredDefinitionFields(String name) throws Exception {
        SavedSearchRequest patch = json.readValue("{\"" + name + "\":null}", SavedSearchRequest.class);
        assertThrows(IllegalArgumentException.class, () -> service.update(10L, patch));
    }

    @Test
    void pauseAndResumeKeepFiltersAndName() {
        SavedSearchRequest patch = new SavedSearchRequest();
        patch.setPaused(true);
        assertTrue(service.update(10L, patch).paused());
        patch.setPaused(false);
        assertFalse(service.update(10L, patch).paused());
        assertEquals("Original", saved.getName());
    }

    @Test
    void replayPreservesDefinitionAndPassesFreshPaginationEvenWhilePaused() {
        saved.setPaused(true);
        PropertySearchResponse expected = new PropertySearchResponse(List.of(), 2, 5, 0, 0, false, true);
        when(propertySearch.search(any())).thenReturn(expected);
        assertSame(expected, service.results(10L, 2, 5));
        ArgumentCaptor<PropertySearchRequest> capture = ArgumentCaptor.forClass(PropertySearchRequest.class);
        verify(propertySearch).search(capture.capture());
        assertEquals(31L, capture.getValue().getCountyId());
        assertEquals(new BigDecimal("3"), capture.getValue().getMinBedrooms());
        assertEquals("bedrooms", capture.getValue().getSortBy());
        assertEquals("desc", capture.getValue().getDirection());
        assertEquals(2, capture.getValue().getPage());
        assertEquals(5, capture.getValue().getSize());
        assertFalse(saved.getFilters().has("page"));
        verify(searches, never()).saveAndFlush(any());
    }

    @Test
    void futureVersionsAreReadableButNotSilentlyReplayed() {
        saved.setFiltersVersion(99);
        assertEquals(99, service.get(10L).filtersVersion());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.results(10L, 0, 20)).getStatusCode());
        verifyNoInteractions(propertySearch);
    }

    @Test
    void replacingFiltersCanRepairAnUnsupportedVersion() {
        saved.setFiltersVersion(99);
        assertEquals(1, service.update(10L, request()).filtersVersion());
    }

    @Test
    void invalidStoredPayloadReturns409() throws Exception {
        saved.setFilters(json.readTree("{\"unknownFilter\":true}"));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.results(10L, 0, 20)).getStatusCode());
        verifyNoInteractions(propertySearch);
    }

    @Test
    void dtoCopiesCannotMutateStoredFilters() {
        ObjectNode copy = (ObjectNode) service.get(10L).filters();
        copy.put("countyId", 99);
        assertEquals(31, saved.getFilters().get("countyId").asInt());
    }

    @Test
    void allOperationsRejectForeignAndMissingIds() {
        assertThrows(ResponseStatusException.class, () -> service.get(99L));
        assertThrows(ResponseStatusException.class, () -> service.update(99L, request()));
        assertThrows(ResponseStatusException.class, () -> service.delete(99L));
        assertThrows(ResponseStatusException.class, () -> service.results(99L, 0, 20));
        verifyNoInteractions(propertySearch);
    }

    @Test
    void listsOnlyTheCurrentUsersDefinitionsAndPreservesEmptyPages() {
        when(searches.findByUser_Id(eq(7L), any())).thenReturn(new PageImpl<>(List.of(saved)));
        assertEquals(1, service.list(0, 20).totalElements());
        when(searches.findByUser_Id(eq(7L), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        assertTrue(service.list(0, 20).content().isEmpty());
    }

    @Test
    void deletesOnlyAnOwnedDefinition() {
        service.delete(10L);
        verify(searches).delete(saved);
        verifyNoInteractions(propertySearch);
    }

    @Test
    void requiresAnAuthenticatedIdentity() {
        when(currentUser.getCurrentUserEntity()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> service.get(10L)).getStatusCode());
    }

    @Test
    void validatesIdsAndReplayPagination() {
        assertThrows(IllegalArgumentException.class, () -> service.get(0L));
        assertThrows(IllegalArgumentException.class, () -> service.list(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.results(10L, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> service.results(10L, Integer.MAX_VALUE, 100));
        verifyNoInteractions(propertySearch);
    }
}
