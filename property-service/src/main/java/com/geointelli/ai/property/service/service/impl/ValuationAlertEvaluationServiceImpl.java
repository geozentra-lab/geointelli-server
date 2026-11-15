package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.dto.AlertSearchDefinitionDTO;
import com.geointelli.ai.property.service.dto.ValuationChangePayloadDTO;
import com.geointelli.ai.property.service.entity.AlertCadence;
import com.geointelli.ai.property.service.entity.AlertEvaluationState;
import com.geointelli.ai.property.service.entity.AlertEventType;
import com.geointelli.ai.property.service.entity.AlertOutboxEvent;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.entity.ValuationChangeDirection;
import com.geointelli.ai.property.service.repository.AlertMonitoringRepository;
import com.geointelli.ai.property.service.repository.AlertOutboxRepository;
import com.geointelli.ai.property.service.repository.ValuationAlertRepository;
import com.geointelli.ai.property.service.repository.ValuationAlertRepository.Version;
import com.geointelli.ai.property.service.service.AlertSearchDefinitionService;
import com.geointelli.ai.property.service.service.PropertySearchService;
import com.geointelli.ai.property.service.service.ValuationAlertEvaluationService;

/**
 * Uses the existing scheduler, rule lock and outbox; Python publication never
 * calls this service.
 */
@Service
public class ValuationAlertEvaluationServiceImpl implements ValuationAlertEvaluationService {
    private static final int MAX_VERSIONS = 100;
    private final ValuationAlertRepository history;
    private final AlertOutboxRepository outbox;
    private final AlertMonitoringRepository monitoring;
    private final AlertSearchDefinitionService definitions;
    private final PropertySearchService search;
    private final AlertMonitoringProperties settings;
    private final ObjectMapper json;
    private final Clock clock;

    /**
     * Injects publication readers and the same microsecond clock used by alert
     * event delivery.
     */
    public ValuationAlertEvaluationServiceImpl(ValuationAlertRepository history, AlertOutboxRepository outbox,
            AlertMonitoringRepository monitoring, AlertSearchDefinitionService definitions,
            PropertySearchService search,
            AlertMonitoringProperties settings, ObjectMapper json, @Qualifier("alertClock") Clock clock) {
        this.history = history;
        this.outbox = outbox;
        this.monitoring = monitoring;
        this.definitions = definitions;
        this.search = search;
        this.settings = settings;
        this.json = json;
        this.clock = clock;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(AlertRule rule, AlertEvaluationState state) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AlertSearchDefinitionDTO definition;
        try {
            definition = rule.getSavedSearch() == null ? null : definitions.resolve(rule);
        } catch (IllegalArgumentException invalid) {
            state.setRunning(false);
            state.setStatus("BLOCKED");
            state.setLastErrorCode("INVALID_OR_UNSUPPORTED_SEARCH");
            state.setNextEvaluationAt(now.plusSeconds(settings.getRetryDelaySeconds()));
            return;
        }
        String target = definition == null ? "property:" + rule.getProperty().getId() : definition.hash();
        String hash = hash(target + ":" + rule.getMinimumChangePercent().stripTrailingZeros().toPlainString() + ":"
                + rule.getChangeDirection());
        String stamp = definition == null ? String.valueOf(rule.getUpdatedAt()) : definition.stamp();
        if (!state.isRunning() || !hash.equals(state.getRunHash()) || !stamp.equals(state.getRunStamp())
                || state.getStartedAt().plus(Duration.ofMinutes(settings.getMaxScanAgeMinutes())).isBefore(now)) {
            if (!hash.equals(state.getBaselineHash()))
                history.clear(rule.getId());
            state.setGeneration(state.getGeneration() + 1);
            state.setRunHash(hash);
            state.setRunStamp(stamp);
            state.setCursorId(0);
            state.setMatchedCount(0);
            state.setStartedAt(now);
            state.setRunning(true);
        }
        state.setStatus("SCANNING");
        state.setLastErrorCode(null);
        int propertyLimit = Math.min(100, settings.getIdBatchSize());
        List<Long> ids = definition == null
                ? (state.getCursorId() < rule.getProperty().getId() ? List.of(rule.getProperty().getId()) : List.of())
                : search.findMatchingIds(definition.filters(), state.getCursorId(), propertyLimit);
        if (ids.size() > propertyLimit)
            throw new IllegalStateException("Oversized valuation property batch");
        int remaining = MAX_VERSIONS;
        long previous = state.getCursorId();
        for (Long propertyId : ids) {
            if (propertyId == null || propertyId <= previous)
                throw new IllegalStateException("Non-increasing valuation property batch");
            previous = propertyId;
            int used = observe(rule, state, propertyId, remaining);
            remaining -= used;
            // An exact full page is continued on the next transaction, even if that last
            // page was final.
            if (remaining == 0) {
                state.setNextEvaluationAt(now);
                return;
            }
            state.setCursorId(propertyId);
            state.setMatchedCount(state.getMatchedCount() + 1);
        }
        if (definition != null && ids.size() == propertyLimit) {
            state.setNextEvaluationAt(now);
            return;
        }
        boolean baseline = state.getBaselineGeneration() == null || !hash.equals(state.getBaselineHash());
        history.retainGeneration(rule.getId(), state.getGeneration());
        state.setBaselineHash(hash);
        state.setBaselineGeneration(state.getGeneration());
        state.setRunning(false);
        state.setStatus(baseline ? "BASELINED" : "COMPLETED");
        state.setLastEvaluatedAt(now);
        state.setFailureCount(0);
        state.setNextEvaluationAt(
                now.plus(rule.getCadence() == AlertCadence.DAILY ? Duration.ofDays(1) : Duration.ofDays(7)));
    }

    private int observe(AlertRule rule, AlertEvaluationState state, long propertyId, int limit) {
        ValuationAlertRepository.Latest latest = history.latest(propertyId);
        ValuationAlertRepository.Observation observation = history.observation(rule.getId(), propertyId);
        if (observation == null || latest == null || !latest.currentUsable() || !usable(latest.version())) {
            history.observe(rule.getId(), propertyId, latest == null ? 0 : latest.cursorBoundary(),
                    latest != null && latest.currentUsable() && usable(latest.version()) ? latest.version().id() : null,
                    state.getGeneration());
            return 0;
        }
        List<Version> versions = history.after(propertyId, observation.cursorHistoryId(), limit);
        Version old = observation.comparisonHistoryId() == null ? null
                : history.version(propertyId, observation.comparisonHistoryId());
        long cursor = observation.cursorHistoryId();
        for (Version next : versions) {
            if (next.id() <= cursor)
                throw new IllegalStateException("Non-increasing valuation version batch");
            cursor = next.id();
            // Late archival of pre-history data is not a new model publication.
            if (!"PUBLISHED".equals(next.provenance())) {
                if (old == null && next.id() == latest.version().id() && usable(next))
                    old = next;
                continue;
            }
            if (usable(old) && usable(next) && qualifies(rule, old.value(), next.value()))
                publish(rule, state, propertyId, old, next);
            old = usable(next) ? next : null;
        }
        history.observe(rule.getId(), propertyId, cursor, old == null ? null : old.id(), state.getGeneration());
        return versions.size();
    }

    private boolean usable(Version version) {
        return version != null && ("supported".equals(version.status()) || "limited".equals(version.status()))
                && version.value() != null && version.value().signum() > 0;
    }

    private boolean qualifies(AlertRule rule, BigDecimal old, BigDecimal next) {
        BigDecimal amount = next.subtract(old);
        if (amount.signum() == 0
                || (rule.getChangeDirection() == ValuationChangeDirection.INCREASE && amount.signum() < 0)
                || (rule.getChangeDirection() == ValuationChangeDirection.DECREASE && amount.signum() > 0))
            return false;
        // Compare exact amounts before rounding the display percentage, including
        // threshold boundary cases.
        return amount.abs().multiply(BigDecimal.valueOf(100))
                .compareTo(old.multiply(rule.getMinimumChangePercent())) >= 0;
    }

    private void publish(AlertRule rule, AlertEvaluationState state, long propertyId, Version old, Version next) {
        String change = "valuation:" + propertyId + ":history:" + next.id();
        if (outbox.existsByRuleIdAndSourceChangeId(rule.getId(), change))
            return;
        String recipient = monitoring.recipient(rule.getUser().getId());
        if (recipient == null || recipient.isBlank())
            throw new IllegalStateException("Missing valuation event recipient");
        Instant finished = clock.instant();
        if (finished.isBefore(state.getStartedAt()))
            finished = state.getStartedAt();
        if (finished.isBefore(old.loadedAt()))
            finished = old.loadedAt();
        if (finished.isBefore(next.loadedAt()))
            finished = next.loadedAt();
        finished = finished.truncatedTo(ChronoUnit.MICROS);
        BigDecimal amount = next.value().subtract(old.value());
        BigDecimal percent = amount.multiply(BigDecimal.valueOf(100)).divide(old.value(), 4, RoundingMode.HALF_UP);
        AlertOutboxEvent event = new AlertOutboxEvent();
        event.setRuleId(rule.getId());
        event.setGeneration(state.getGeneration());
        event.setSavedSearchId(rule.getSavedSearch() == null ? null : rule.getSavedSearch().getId());
        event.setPropertyId(propertyId);
        event.setRecipientKeycloakId(recipient);
        event.setEventType(AlertEventType.VALUATION_CHANGED);
        event.setSourceChangeId(change);
        event.setOccurredAt(finished);
        event.setNextAttemptAt(finished);
        event.setPayload(
                json.valueToTree(new ValuationChangePayloadDTO(rule.getName(), "USD", snapshot(old), snapshot(next),
                        amount, percent, rule.getMinimumChangePercent(), rule.getChangeDirection(),
                        amount.signum() > 0 ? ValuationChangeDirection.INCREASE : ValuationChangeDirection.DECREASE,
                        state.getStartedAt(), finished)));
        outbox.save(event);
    }

    private ValuationChangePayloadDTO.Snapshot snapshot(Version version) {
        return new ValuationChangePayloadDTO.Snapshot(version.id(), version.runId(), version.modelVersion(),
                version.predictionDate(), version.status(), version.value(), version.loadedAt());
    }

    private String hash(String input) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
