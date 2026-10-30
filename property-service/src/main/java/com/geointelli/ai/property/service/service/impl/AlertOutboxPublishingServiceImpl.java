package com.geointelli.ai.property.service.service.impl;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.dto.AlertEventEnvelopeDTO;
import com.geointelli.ai.property.service.entity.AlertOutboxEvent;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.repository.AlertMonitoringRepository;
import com.geointelli.ai.property.service.repository.AlertOutboxRepository;
import com.geointelli.ai.property.service.repository.AlertRuleRepository;
import com.geointelli.ai.property.service.service.AlertEventPublisher;
import com.geointelli.ai.property.service.service.AlertOutboxPublishingService;

/** Retains failed deliveries and uses stable event IDs for at-least-once delivery. */
@Service
public class AlertOutboxPublishingServiceImpl implements AlertOutboxPublishingService {
    private final AlertMonitoringRepository monitoring;
    private final AlertOutboxRepository outbox;
    private final AlertRuleRepository rules;
    private final AlertEventPublisher publisher;
    private final AlertMonitoringProperties settings;
    private final Clock clock;
    public AlertOutboxPublishingServiceImpl(AlertMonitoringRepository monitoring, AlertOutboxRepository outbox,
            AlertRuleRepository rules, AlertEventPublisher publisher, AlertMonitoringProperties settings,
            @Qualifier("alertClock") Clock clock) {
        this.monitoring=monitoring; this.outbox=outbox; this.rules=rules; this.publisher=publisher;
        this.settings=settings; this.clock=clock;
    }
    @Override
    @Transactional(timeout = 45)
    public boolean processNext() {
        if (!settings.isEnabled() || !settings.getPublisher().isEnabled()) return false;
        Instant now = clock.instant();
        Long id = monitoring.lockNextOutbox(now);
        if (id == null) return false;
        AlertOutboxEvent event = outbox.findById(id).orElseThrow();
        AlertRule rule = rules.findById(event.getRuleId()).orElse(null);
        if (rule == null) { event.setStatus("CANCELLED"); return true; }
        if (rule.isPaused() || (rule.getSavedSearch()!=null && rule.getSavedSearch().isPaused())) {
            event.setNextAttemptAt(now.plusSeconds(60)); return true;
        }
        AlertEventEnvelopeDTO envelope = new AlertEventEnvelopeDTO("property-alert-"+event.getId(),
            "property-service", 1, event.getEventType(), event.getRecipientKeycloakId(), event.getRuleId(),
            event.getSavedSearchId(), event.getPropertyId(),
            "rule:"+event.getRuleId()+":generation:"+event.getGeneration(), event.getOccurredAt(), event.getPayload());
        try {
            publisher.publish(envelope);
            event.setStatus("SENT"); event.setSentAt(clock.instant()); event.setLastErrorCode(null);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            int attempts = event.getAttempts()+1;
            event.setAttempts(attempts); event.setLastErrorCode("BROKER_DELIVERY_FAILED");
            event.setStatus(attempts>=settings.getPublisher().getMaxAttempts() ? "FAILED" : "PENDING");
            long delay = Math.min(3600L, settings.getPublisher().getRetryBaseSeconds() * (1L << Math.min(10, attempts-1)));
            event.setNextAttemptAt(clock.instant().plusSeconds(delay));
        }
        outbox.save(event);
        return true;
    }
}
