package com.geointelli.ai.property.service.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.dto.AlertSearchDefinitionDTO;
import com.geointelli.ai.property.service.dto.NewMatchPayloadDTO;
import com.geointelli.ai.property.service.entity.AlertCadence;
import com.geointelli.ai.property.service.entity.AlertEvaluationState;
import com.geointelli.ai.property.service.entity.AlertEventType;
import com.geointelli.ai.property.service.entity.AlertOutboxEvent;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.exception.AlertEvaluationException;
import com.geointelli.ai.property.service.repository.AlertEvaluationStateRepository;
import com.geointelli.ai.property.service.repository.AlertMonitoringRepository;
import com.geointelli.ai.property.service.repository.AlertOutboxRepository;
import com.geointelli.ai.property.service.repository.AlertRuleRepository;
import com.geointelli.ai.property.service.service.AlertEvaluationService;
import com.geointelli.ai.property.service.service.AlertSearchDefinitionService;
import com.geointelli.ai.property.service.service.PropertySearchService;

/** Scans all matching IDs in bounded batches and commits the baseline and outbox event atomically. */
@Service
public class AlertEvaluationServiceImpl implements AlertEvaluationService {
    private final AlertRuleRepository rules;
    private final AlertEvaluationStateRepository states;
    private final AlertMonitoringRepository monitoring;
    private final AlertOutboxRepository outbox;
    private final AlertSearchDefinitionService definitions;
    private final PropertySearchService search;
    private final AlertMonitoringProperties settings;
    private final ObjectMapper json;
    private final Clock clock;

    public AlertEvaluationServiceImpl(AlertRuleRepository rules, AlertEvaluationStateRepository states,
            AlertMonitoringRepository monitoring, AlertOutboxRepository outbox,
            AlertSearchDefinitionService definitions, PropertySearchService search,
            AlertMonitoringProperties settings, ObjectMapper json, @Qualifier("alertClock") Clock clock) {
        this.rules = rules; this.states = states; this.monitoring = monitoring; this.outbox = outbox;
        this.definitions = definitions; this.search = search; this.settings = settings; this.json = json; this.clock = clock;
    }

    @Override
    @Transactional(timeout = 30)
    public void prepare() { if (settings.isEnabled()) monitoring.seedStates(clock.instant()); }

    @Override
    @Transactional(timeout = 120)
    public boolean processNextBatch() {
        if (!settings.isEnabled()) return false;
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Long id = monitoring.lockNextDueRule(now);
        if (id == null) return false;
        try {
            AlertRule rule = rules.findById(id).orElseThrow();
            AlertEvaluationState state = states.findById(id).orElseThrow();
            if (rule.getEventType() != AlertEventType.NEW_MATCH) {
                block(state, "UNSUPPORTED_SOURCE", now); return true;
            }
            AlertSearchDefinitionDTO definition;
            try { definition = definitions.resolve(rule); }
            catch (IllegalArgumentException invalid) {
                block(state, "INVALID_OR_UNSUPPORTED_SEARCH", now); return true;
            }
            if (!state.isRunning() || !definition.hash().equals(state.getRunHash())
                    || !definition.stamp().equals(state.getRunStamp())
                    || state.getStartedAt().plus(Duration.ofMinutes(settings.getMaxScanAgeMinutes())).isBefore(now)) {
                start(state, definition, now);
            }
            state.setStatus("SCANNING");
            state.setLastErrorCode(null);
            List<Long> ids = search.findMatchingIds(definition.filters(), state.getCursorId(), settings.getIdBatchSize());
            validateCursor(ids, state.getCursorId());
            if (!ids.isEmpty()) {
                monitoring.addMatches(id, state.getGeneration(), ids);
                state.setCursorId(ids.get(ids.size() - 1));
                state.setMatchedCount(state.getMatchedCount() + ids.size());
            }
            if (ids.size() < settings.getIdBatchSize()) complete(rule, state, definition, clock.instant());
            else state.setNextEvaluationAt(now);
            states.save(state);
            return true;
        } catch (RuntimeException error) {
            throw new AlertEvaluationException(id, error);
        }
    }

    private void start(AlertEvaluationState state, AlertSearchDefinitionDTO definition, Instant now) {
        monitoring.clearStaging(state.getRuleId(), state.getBaselineGeneration());
        state.setGeneration(state.getGeneration() + 1);
        state.setRunHash(definition.hash()); state.setRunStamp(definition.stamp());
        state.setCursorId(0); state.setMatchedCount(0); state.setStartedAt(now);
        state.setRunning(true); state.setStatus("SCANNING"); state.setLastErrorCode(null);
    }

    private void complete(AlertRule rule, AlertEvaluationState state, AlertSearchDefinitionDTO definition, Instant now) {
        // PostgreSQL timestamps retain microseconds; JSON must use the same precision.
        // Normalize once before assigning both the envelope time and payload finish time.
        now = now.truncatedTo(ChronoUnit.MICROS);
        boolean baseline = state.getBaselineGeneration() == null || !definition.hash().equals(state.getBaselineHash());
        if (!baseline) {
            long count = monitoring.countNew(rule.getId(), state.getGeneration(), state.getBaselineGeneration());
            if (count > 0) {
                List<Long> preview = monitoring.previewNew(rule.getId(), state.getGeneration(), state.getBaselineGeneration());
                String recipient = monitoring.recipient(rule.getUser().getId());
                if (recipient == null || recipient.isBlank()) throw new IllegalStateException("Missing event recipient");
                AlertOutboxEvent event = new AlertOutboxEvent();
                event.setRuleId(rule.getId()); event.setGeneration(state.getGeneration());
                event.setSavedSearchId(rule.getSavedSearch().getId()); event.setRecipientKeycloakId(recipient);
                event.setEventType(AlertEventType.NEW_MATCH); event.setOccurredAt(now); event.setNextAttemptAt(now);
                event.setPayload(json.valueToTree(new NewMatchPayloadDTO(rule.getName(), definition.hash(),
                    state.getMatchedCount(), count, preview, count > preview.size(), state.getStartedAt(), now)));
                outbox.save(event);
            }
        }
        monitoring.retainOnly(rule.getId(), state.getGeneration());
        state.setBaselineGeneration(state.getGeneration()); state.setBaselineHash(definition.hash());
        state.setRunning(false); state.setStatus(baseline ? "BASELINED" : "COMPLETED");
        state.setLastEvaluatedAt(now); state.setLastErrorCode(null); state.setFailureCount(0);
        state.setNextEvaluationAt(now.plus(rule.getCadence() == AlertCadence.DAILY ? Duration.ofDays(1) : Duration.ofDays(7)));
    }

    private void block(AlertEvaluationState state, String code, Instant now) {
        monitoring.clearStaging(state.getRuleId(), state.getBaselineGeneration());
        state.setRunning(false); state.setStatus("BLOCKED"); state.setLastErrorCode(code);
        state.setNextEvaluationAt(now.plusSeconds(settings.getRetryDelaySeconds()));
        states.save(state);
    }

    /** Fails the transaction rather than silently truncating or looping on an invalid ID batch. */
    private void validateCursor(List<Long> ids, long cursor) {
        if (ids.size() > settings.getIdBatchSize()) throw new IllegalStateException("Oversized ID batch");
        for (Long id : ids) {
            if (id == null || id <= cursor) throw new IllegalStateException("Non-increasing property ID batch");
            cursor = id;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long ruleId) {
        AlertRule rule = rules.findById(ruleId).orElse(null);
        if (rule == null) return;
        // Re-lock in the same order used by normal processing and user edits.
        if (rules.findOwnedForUpdate(ruleId, rule.getUser().getId()).isEmpty()) return;
        states.findById(ruleId).ifPresent(state -> {
            state.setStatus("FAILED"); state.setLastErrorCode("EVALUATION_BATCH_FAILED");
            state.setFailureCount(state.getFailureCount() + 1);
            state.setNextEvaluationAt(clock.instant().plusSeconds(settings.getRetryDelaySeconds()));
            states.save(state);
        });
    }
}
