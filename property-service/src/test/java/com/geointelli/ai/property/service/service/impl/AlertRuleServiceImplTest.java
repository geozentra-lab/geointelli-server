package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.AlertRuleDTO;
import com.geointelli.ai.property.service.dto.AlertRuleRequest;
import com.geointelli.ai.property.service.entity.AlertCadence;
import com.geointelli.ai.property.service.entity.AlertEventType;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.entity.SavedSearchCadence;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.AlertRuleMapper;
import com.geointelli.ai.property.service.repository.AlertRuleRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.SavedSearchRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import com.geointelli.ai.property.service.service.AlertEvaluationScheduleService;

/** Tests ownership, stable target/trigger identity and effective pause semantics without external systems. */
class AlertRuleServiceImplTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final AlertRuleRepository rules = mock(AlertRuleRepository.class);
    private final SavedSearchRepository searches = mock(SavedSearchRepository.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final AlertRuleServiceImpl service = new AlertRuleServiceImpl(rules, searches, properties,
        currentUser, new AlertRuleMapper(), FACTORY.getValidator(), mock(AlertEvaluationScheduleService.class));
    private SavedSearch search;
    private AlertRule rule;
    private User user;

    @AfterAll
    static void cleanup() { FACTORY.close(); }

    @BeforeEach
    void setup() {
        user = new User();
        user.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
        search = new SavedSearch();
        search.setId(10L);
        search.setUser(user);
        search.setCadence(SavedSearchCadence.WEEKLY);
        when(searches.findOwnedForUpdate(10L, 7L)).thenReturn(Optional.of(search));
        Property property = new Property();
        property.setId(100L);
        when(properties.existsById(100L)).thenReturn(true);
        when(properties.getReferenceById(100L)).thenReturn(property);
        rule = new AlertRule();
        rule.setId(20L);
        rule.setUser(user);
        rule.setName("Original");
        rule.setEventType(AlertEventType.NEW_MATCH);
        rule.setSavedSearch(search);
        when(rules.findByIdAndUser_Id(20L, 7L)).thenReturn(Optional.of(rule));
        when(rules.findOwnedForUpdate(20L, 7L)).thenReturn(Optional.of(rule));
        when(rules.saveAndFlush(any())).thenAnswer(call -> {
            AlertRule saved = call.getArgument(0);
            if (saved.getId() == null) saved.setId(21L);
            return saved;
        });
    }

    private AlertRuleRequest searchRequest() {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setName("  New Miami homes  ");
        request.setEventType(AlertEventType.NEW_MATCH);
        request.setSavedSearchId(10L);
        return request;
    }

    private AlertRuleRequest propertyRequest() {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setName("Property price");
        request.setEventType(AlertEventType.LISTING_PRICE_CHANGED);
        request.setPropertyId(100L);
        return request;
    }

    @Test
    void createsOwnedSearchRuleAndInheritsCadenceOnlyAtCreation() {
        AlertRuleDTO result = service.create(searchRequest());
        assertEquals("New Miami homes", result.name());
        assertEquals(10L, result.savedSearchId());
        assertNull(result.propertyId());
        assertEquals(AlertCadence.WEEKLY, result.cadence());
        assertFalse(result.paused());
        assertFalse(result.effectivePaused());
        ArgumentCaptor<AlertRule> saved = ArgumentCaptor.forClass(AlertRule.class);
        verify(rules).saveAndFlush(saved.capture());
        assertSame(user, saved.getValue().getUser());
        verify(searches).findOwnedForUpdate(10L, 7L);
        verifyNoInteractions(properties);
    }

    @Test
    void explicitCadenceAndPauseOverrideDefaults() {
        AlertRuleRequest request = searchRequest();
        request.setCadence(AlertCadence.DAILY);
        request.setPaused(true);
        AlertRuleDTO result = service.create(request);
        assertEquals(AlertCadence.DAILY, result.cadence());
        assertTrue(result.paused());
        assertTrue(result.effectivePaused());
    }

    @Test
    void propertyRuleUsesInternalIdAndDailyDefault() {
        AlertRuleDTO result = service.create(propertyRequest());
        assertEquals(100L, result.propertyId());
        assertNull(result.savedSearchId());
        assertEquals(AlertCadence.DAILY, result.cadence());
        verify(properties, never()).save(any());
        verifyNoInteractions(searches);
    }

    @Test
    void valuationEventsCanUseEitherTarget() {
        AlertRuleRequest property = propertyRequest();
        property.setEventType(AlertEventType.VALUATION_CHANGED);
        assertEquals(AlertEventType.VALUATION_CHANGED, service.create(property).eventType());
        AlertRuleRequest saved = searchRequest();
        saved.setEventType(AlertEventType.VALUATION_CHANGED);
        assertEquals(AlertEventType.VALUATION_CHANGED, service.create(saved).eventType());
    }

    @Test
    void listingPriceEventsCanMonitorSearchMatches() {
        AlertRuleRequest request = searchRequest();
        request.setEventType(AlertEventType.LISTING_PRICE_CHANGED);
        assertEquals(10L, service.create(request).savedSearchId());
    }

    @Test
    void requiresExactlyOneTarget() {
        AlertRuleRequest request = searchRequest();
        request.setSavedSearchId(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        request.setSavedSearchId(10L);
        request.setPropertyId(100L);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(rules, never()).saveAndFlush(any());
    }

    @Test
    void newMatchCannotMonitorASingleProperty() {
        AlertRuleRequest request = propertyRequest();
        request.setEventType(AlertEventType.NEW_MATCH);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void missingOrForeignSearchReturns404WithoutCreatingRule() {
        AlertRuleRequest request = searchRequest();
        request.setSavedSearchId(99L);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.create(request)).getStatusCode());
        verify(rules, never()).saveAndFlush(any());
    }

    @Test
    void missingPropertyReturns404() {
        AlertRuleRequest request = propertyRequest();
        request.setPropertyId(999L);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.create(request)).getStatusCode());
        verify(rules, never()).saveAndFlush(any());
    }

    @Test
    void duplicateSearchAndPropertyEventsReturn409() {
        when(rules.existsByUser_IdAndSavedSearch_IdAndEventType(7L, 10L, AlertEventType.NEW_MATCH)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.create(searchRequest())).getStatusCode());
        when(rules.existsByUser_IdAndProperty_IdAndEventType(7L, 100L, AlertEventType.LISTING_PRICE_CHANGED)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.create(propertyRequest())).getStatusCode());
        verify(rules, never()).saveAndFlush(any());
    }

    @Test
    void requiresValidNameEventAndPositiveTargetIds() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new AlertRuleRequest()));
        AlertRuleRequest request = searchRequest();
        request.setName("  ");
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        request.setName("x".repeat(121));
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        request.setName("Valid");
        request.setSavedSearchId(-1L);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void getsOnlyTheOwnersRule() {
        assertEquals(20L, service.get(20L).id());
        verify(rules).findByIdAndUser_Id(20L, 7L);
        verify(rules, never()).findById(anyLong());
    }

    @Test
    void foreignRulesAreUnavailableForReadUpdateAndDelete() {
        assertThrows(ResponseStatusException.class, () -> service.get(99L));
        assertThrows(ResponseStatusException.class, () -> service.update(99L, new AlertRuleRequest()));
        assertThrows(ResponseStatusException.class, () -> service.delete(99L));
        verify(rules, never()).saveAndFlush(any());
        verify(rules, never()).delete(any());
    }

    @Test
    void listsOnlyOwnedRulesWithStablePaging() {
        when(rules.findByUser_Id(eq(7L), any())).thenReturn(new PageImpl<>(List.of(rule)));
        assertEquals(1, service.list(0, 20).totalElements());
        ArgumentCaptor<PageRequest> page = ArgumentCaptor.forClass(PageRequest.class);
        verify(rules).findByUser_Id(eq(7L), page.capture());
        assertEquals("id: DESC", page.getValue().getSort().toString());
    }

    @Test
    void emptyPagesStayEmpty() {
        when(rules.findByUser_Id(eq(7L), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        assertTrue(service.list(0, 20).content().isEmpty());
    }

    @Test
    void patchChangesOnlySuppliedMetadata() {
        AlertRuleRequest patch = new AlertRuleRequest();
        patch.setName(" Renamed ");
        AlertRuleDTO result = service.update(20L, patch);
        assertEquals("Renamed", result.name());
        assertEquals(10L, result.savedSearchId());
        assertEquals(AlertEventType.NEW_MATCH, result.eventType());
        assertEquals(AlertCadence.DAILY, result.cadence());
        verify(rules).findOwnedForUpdate(20L, 7L);
    }

    @Test
    void targetAndEventIdentityCannotBeChanged() {
        AlertRuleRequest target = new AlertRuleRequest();
        target.setSavedSearchId(99L);
        assertThrows(IllegalArgumentException.class, () -> service.update(20L, target));
        AlertRuleRequest trigger = new AlertRuleRequest();
        trigger.setEventType(AlertEventType.VALUATION_CHANGED);
        assertThrows(IllegalArgumentException.class, () -> service.update(20L, trigger));
        verify(rules, never()).saveAndFlush(any());
    }

    @Test
    void identicalTargetAndEventFieldsAreHarmlessOnPatch() {
        AlertRuleRequest patch = new AlertRuleRequest();
        patch.setSavedSearchId(10L);
        patch.setEventType(AlertEventType.NEW_MATCH);
        assertEquals(10L, service.update(20L, patch).savedSearchId());
    }

    @Test
    void invalidPatchDoesNotPartiallyRenameTheRule() {
        AlertRuleRequest patch = new AlertRuleRequest();
        patch.setName("Should not apply");
        patch.setPropertyId(100L);
        assertThrows(IllegalArgumentException.class, () -> service.update(20L, patch));
        assertEquals("Original", rule.getName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "eventType", "cadence", "paused"})
    void explicitNullCannotClearRequiredFields(String field) throws Exception {
        AlertRuleRequest patch = new ObjectMapper().readValue("{\"" + field + "\":null}", AlertRuleRequest.class);
        assertThrows(IllegalArgumentException.class, () -> service.update(20L, patch));
    }

    @Test
    void savedSearchPauseOverridesUnpausedRuleAndDoesNotOverwriteIt() {
        search.setPaused(true);
        AlertRuleDTO result = service.get(20L);
        assertFalse(result.paused());
        assertTrue(result.effectivePaused());
        AlertRuleRequest patch = new AlertRuleRequest();
        patch.setPaused(false);
        assertTrue(service.update(20L, patch).effectivePaused());
        search.setPaused(false);
        assertFalse(service.get(20L).effectivePaused());
    }

    @Test
    void ownPauseSurvivesSearchResuming() {
        rule.setPaused(true);
        search.setPaused(false);
        assertTrue(service.get(20L).effectivePaused());
    }

    @Test
    void searchCadenceEditsDoNotOverwriteAnExistingRule() {
        rule.setCadence(AlertCadence.DAILY);
        search.setCadence(SavedSearchCadence.WEEKLY);
        assertEquals(AlertCadence.DAILY, service.get(20L).cadence());
    }

    @Test
    void deleteRemovesOnlyTheRule() {
        service.delete(20L);
        verify(rules).delete(rule);
        verifyNoInteractions(searches, properties);
    }

    @Test
    void validatesPaginationAndIdBounds() {
        assertThrows(IllegalArgumentException.class, () -> service.list(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.list(0, 101));
        assertThrows(IllegalArgumentException.class, () -> service.list(Integer.MAX_VALUE, 100));
        assertThrows(IllegalArgumentException.class, () -> service.get(null));
        assertThrows(IllegalArgumentException.class, () -> service.delete(0L));
    }

    @Test
    void requiresBearerIdentity() {
        when(currentUser.getCurrentUserEntity()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
            () -> service.list(0, 20)).getStatusCode());
    }
}
