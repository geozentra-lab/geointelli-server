package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.dto.AlertEventEnvelopeDTO;
import com.geointelli.ai.property.service.entity.*;
import com.geointelli.ai.property.service.repository.*;
import com.geointelli.ai.property.service.service.AlertEventPublisher;

class AlertOutboxPublishingServiceImplTest {
    final AlertMonitoringRepository monitoring=mock(AlertMonitoringRepository.class);
    final AlertOutboxRepository outbox=mock(AlertOutboxRepository.class);
    final AlertRuleRepository rules=mock(AlertRuleRepository.class);
    final AlertEventPublisher publisher=mock(AlertEventPublisher.class);
    final AlertMonitoringProperties settings=new AlertMonitoringProperties();
    final Instant now=Instant.parse("2026-10-25T10:00:00Z");
    final AlertOutboxEvent event=new AlertOutboxEvent();
    final AlertRule rule=new AlertRule();
    final AlertOutboxPublishingServiceImpl service=new AlertOutboxPublishingServiceImpl(monitoring,outbox,rules,
        publisher,settings,Clock.fixed(now,ZoneOffset.UTC));
    @BeforeEach void setup() {
        settings.setEnabled(true); settings.getPublisher().setEnabled(true);
        event.setId(5L); event.setRuleId(1L); event.setGeneration(2); event.setEventType(AlertEventType.NEW_MATCH);
        event.setOccurredAt(now); event.setRecipientKeycloakId("user-1");
        when(monitoring.lockNextOutbox(now)).thenReturn(5L);
        when(outbox.findById(5L)).thenReturn(Optional.of(event));
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
    }
    @Test void successMarksSentAfterPublishing() throws Exception {
        service.processNext(); assertEquals("SENT",event.getStatus()); assertEquals(now,event.getSentAt());
        ArgumentCaptor<AlertEventEnvelopeDTO> envelope=ArgumentCaptor.forClass(AlertEventEnvelopeDTO.class);
        verify(publisher).publish(envelope.capture()); assertEquals("property-alert-5",envelope.getValue().eventId());
        assertEquals(1,envelope.getValue().eventVersion());
    }
    @Test void retryRetainsStableIdAndBacksOff() throws Exception {
        doThrow(new IllegalStateException()).when(publisher).publish(any());
        service.processNext(); assertEquals("PENDING",event.getStatus()); assertEquals(1,event.getAttempts());
        assertEquals(now.plusSeconds(30),event.getNextAttemptAt());
        service.processNext(); assertEquals(now.plusSeconds(60),event.getNextAttemptAt());
        verify(publisher,times(2)).publish(argThat(e->e.eventId().equals("property-alert-5")));
    }
    @Test void exhaustedRetriesRetainFailedEvent() throws Exception {
        event.setAttempts(9); doThrow(new IllegalStateException()).when(publisher).publish(any());
        service.processNext(); assertEquals("FAILED",event.getStatus()); assertNull(event.getSentAt());
    }
    @Test void deletedRuleCancelsPendingDelivery() {
        when(rules.findById(1L)).thenReturn(Optional.empty()); service.processNext();
        assertEquals("CANCELLED",event.getStatus()); verifyNoInteractions(publisher);
    }
    @Test void pausedRuleDefersWithoutSpendingRetry() {
        rule.setPaused(true); service.processNext(); assertEquals(0,event.getAttempts()); verifyNoInteractions(publisher);
    }
    @Test void disabledPublisherDoesNotClaim() {
        settings.getPublisher().setEnabled(false); assertFalse(service.processNext()); verifyNoInteractions(monitoring);
    }
}
