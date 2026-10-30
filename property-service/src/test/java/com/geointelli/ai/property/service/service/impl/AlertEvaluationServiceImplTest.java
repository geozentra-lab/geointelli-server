package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.dto.AlertSearchDefinitionDTO;
import com.geointelli.ai.property.service.dto.SavedSearchFilters;
import com.geointelli.ai.property.service.entity.*;
import com.geointelli.ai.property.service.exception.AlertEvaluationException;
import com.geointelli.ai.property.service.repository.*;
import com.geointelli.ai.property.service.service.AlertSearchDefinitionService;
import com.geointelli.ai.property.service.service.PropertySearchService;

class AlertEvaluationServiceImplTest {
    final AlertRuleRepository rules=mock(AlertRuleRepository.class);
    final AlertEvaluationStateRepository states=mock(AlertEvaluationStateRepository.class);
    final AlertMonitoringRepository monitoring=mock(AlertMonitoringRepository.class);
    final AlertOutboxRepository outbox=mock(AlertOutboxRepository.class);
    final AlertSearchDefinitionService definitions=mock(AlertSearchDefinitionService.class);
    final PropertySearchService search=mock(PropertySearchService.class);
    final AlertMonitoringProperties settings=new AlertMonitoringProperties();
    final Instant now=Instant.parse("2026-10-25T10:00:00Z");
    final SavedSearchFilters filters=new SavedSearchFilters();
    final AlertRule rule=new AlertRule();
    final AlertEvaluationState state=new AlertEvaluationState();
    final AlertEvaluationServiceImpl service=new AlertEvaluationServiceImpl(rules,states,monitoring,outbox,
        definitions,search,settings,new ObjectMapper().findAndRegisterModules(),Clock.fixed(now,ZoneOffset.UTC));
    @BeforeEach void setup() {
        settings.setEnabled(true); settings.setIdBatchSize(2);
        User user=new User(); user.setId(9L);
        SavedSearch saved=new SavedSearch(); saved.setId(4L); saved.setUser(user);
        rule.setId(1L); rule.setUser(user); rule.setSavedSearch(saved); rule.setName("Miami"); rule.setEventType(AlertEventType.NEW_MATCH);
        state.setRuleId(1L);
        when(monitoring.lockNextDueRule(now)).thenReturn(1L);
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        when(states.findById(1L)).thenReturn(Optional.of(state));
        when(definitions.resolve(rule)).thenReturn(new AlertSearchDefinitionDTO(filters,"hash","stamp"));
        when(search.findMatchingIds(eq(filters),anyLong(),eq(2))).thenReturn(List.of());
    }
    @Test void firstScanIsSilentEvenWithMatches() {
        when(search.findMatchingIds(filters,0,2)).thenReturn(List.of(10L));
        assertTrue(service.processNextBatch());
        assertEquals("BASELINED",state.getStatus()); assertEquals(1,state.getMatchedCount());
        assertEquals(now.plusSeconds(86400),state.getNextEvaluationAt());
        verifyNoInteractions(outbox);
    }
    @Test void fullBatchResumesAtLastIdWithoutPublishingEarly() {
        when(search.findMatchingIds(filters,0,2)).thenReturn(List.of(10L,20L));
        when(search.findMatchingIds(filters,20,2)).thenReturn(List.of(30L));
        service.processNextBatch();
        assertTrue(state.isRunning()); assertEquals(20,state.getCursorId()); verifyNoInteractions(outbox);
        service.processNextBatch();
        assertEquals("BASELINED",state.getStatus()); assertEquals(3,state.getMatchedCount());
        verify(monitoring).addMatches(1,1,List.of(30L));
    }
    @Test void nextScanEmitsOneAggregatedEvent() {
        state.setBaselineGeneration(1L); state.setGeneration(1); state.setBaselineHash("hash");
        when(monitoring.countNew(1,2,1)).thenReturn(150L);
        when(monitoring.previewNew(1,2,1)).thenReturn(List.of(90L));
        when(monitoring.recipient(9)).thenReturn("keycloak-user");
        service.processNextBatch();
        ArgumentCaptor<AlertOutboxEvent> event=ArgumentCaptor.forClass(AlertOutboxEvent.class);
        verify(outbox).save(event.capture());
        assertEquals(150,event.getValue().getPayload().get("newMatchCount").asInt());
        assertTrue(event.getValue().getPayload().get("previewTruncated").asBoolean());
        assertEquals("keycloak-user",event.getValue().getRecipientKeycloakId());
        assertEquals("COMPLETED",state.getStatus());
    }
    @Test void changedFiltersEstablishNewSilentBaseline() {
        state.setBaselineGeneration(1L); state.setGeneration(1); state.setBaselineHash("old");
        service.processNextBatch(); assertEquals("BASELINED",state.getStatus()); verifyNoInteractions(outbox);
    }
    @Test void unchangedMembershipEmitsNothing() {
        state.setBaselineGeneration(1L); state.setGeneration(1); state.setBaselineHash("hash");
        service.processNextBatch(); assertEquals("COMPLETED",state.getStatus()); verifyNoInteractions(outbox);
    }
    @Test void unsupportedSourceIsBlocked() {
        rule.setEventType(AlertEventType.LISTING_PRICE_CHANGED);
        service.processNextBatch(); assertEquals("UNSUPPORTED_SOURCE",state.getLastErrorCode());
        verifyNoInteractions(search,outbox);
    }
    @Test void invalidStoredFiltersAreBlocked() {
        when(definitions.resolve(rule)).thenThrow(new IllegalArgumentException());
        service.processNextBatch(); assertEquals("BLOCKED",state.getStatus()); verifyNoInteractions(search);
    }
    @Test void invalidCursorFailsInsteadOfLosingMatches() {
        when(search.findMatchingIds(filters,0,2)).thenReturn(List.of(20L,10L));
        assertThrows(AlertEvaluationException.class,service::processNextBatch); verifyNoInteractions(outbox);
    }
    @Test void disabledMonitoringDoesNotTouchDatabase() {
        settings.setEnabled(false); assertFalse(service.processNextBatch()); service.prepare();
        verifyNoInteractions(monitoring,rules,states);
    }
    @Test void noDueRuleReturnsImmediately() {
        when(monitoring.lockNextDueRule(now)).thenReturn(null); assertFalse(service.processNextBatch());
        verifyNoInteractions(rules,states);
    }
    @Test void failureKeepsCursorForRetry() {
        state.setRunning(true); state.setCursorId(20);
        when(rules.findOwnedForUpdate(1L,9L)).thenReturn(Optional.of(rule));
        service.recordFailure(1L);
        assertTrue(state.isRunning()); assertEquals(20,state.getCursorId());
        assertEquals(now.plusSeconds(300),state.getNextEvaluationAt());
    }
}
