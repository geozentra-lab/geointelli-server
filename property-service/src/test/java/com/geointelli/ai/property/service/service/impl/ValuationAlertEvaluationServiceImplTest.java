package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.dto.AlertSearchDefinitionDTO;
import com.geointelli.ai.property.service.dto.SavedSearchFilters;
import com.geointelli.ai.property.service.entity.*;
import com.geointelli.ai.property.service.repository.*;
import com.geointelli.ai.property.service.repository.ValuationAlertRepository.*;
import com.geointelli.ai.property.service.service.AlertSearchDefinitionService;
import com.geointelli.ai.property.service.service.PropertySearchService;

class ValuationAlertEvaluationServiceImplTest {
    final ValuationAlertRepository history = mock(ValuationAlertRepository.class);
    final AlertOutboxRepository outbox = mock(AlertOutboxRepository.class);
    final AlertMonitoringRepository monitoring = mock(AlertMonitoringRepository.class);
    final AlertSearchDefinitionService definitions = mock(AlertSearchDefinitionService.class);
    final PropertySearchService search = mock(PropertySearchService.class);
    final AlertMonitoringProperties settings = new AlertMonitoringProperties();
    final Instant now = Instant.parse("2026-11-13T10:00:00.720653200Z");
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final ValuationAlertEvaluationServiceImpl service = new ValuationAlertEvaluationServiceImpl(history, outbox,
            monitoring,
            definitions, search, settings, json, Clock.fixed(now, ZoneOffset.UTC));
    final AlertRule rule = new AlertRule();
    final AlertEvaluationState state = new AlertEvaluationState();
    final Map<Long, Observation> observations = new HashMap<>();
    final List<AlertOutboxEvent> events = new ArrayList<>();
    Version current = version(10, "100000", "supported");

    @BeforeEach
    void setup() {
        User owner = new User();
        owner.setId(7L);
        rule.setUser(owner);
        rule.setId(1L);
        rule.setName("Value watch");
        rule.setEventType(AlertEventType.VALUATION_CHANGED);
        Property property = new Property();
        property.setId(301400L);
        rule.setProperty(property);
        rule.setMinimumChangePercent(new BigDecimal("5"));
        state.setRuleId(1L);
        when(monitoring.recipient(7)).thenReturn("keycloak-recipient");
        when(history.latest(anyLong())).thenAnswer(call -> new Latest(current, current.id(), true));
        when(history.observation(eq(1L), anyLong())).thenAnswer(call -> observations.get(call.getArgument(1)));
        doAnswer(call -> {
            observations.put(call.getArgument(1), new Observation(call.getArgument(2), call.getArgument(3)));
            return null;
        })
                .when(history).observe(eq(1L), anyLong(), anyLong(), nullable(Long.class), anyLong());
        when(outbox.save(any())).thenAnswer(call -> {
            events.add(call.getArgument(0));
            return call.getArgument(0);
        });
    }

    @Test
    void firstEvaluationIsSilentAndUsesTheCurrentFrozenVersion() {
        service.evaluate(rule, state);
        assertThat(state.getStatus()).isEqualTo("BASELINED");
        assertThat(events).isEmpty();
        assertThat(observations.get(301400L).comparisonHistoryId()).isEqualTo(10);
        assertThat(state.getNextEvaluationAt()).isEqualTo(Instant.parse("2026-11-14T10:00:00.720653Z"));
        verifyNoInteractions(search);
    }

    @ParameterizedTest
    @CsvSource({ "95000,DECREASE,1", "94999,ANY,1", "95001,DECREASE,0", "105000,INCREASE,1",
            "110000,DECREASE,0", "90000,INCREASE,0", "100000,ANY,0" })
    void exactThresholdAndDirectionUseEstimatesNotRoundedPercentages(String amount, String direction, int expected) {
        rule.setChangeDirection(ValuationChangeDirection.valueOf(direction));
        baselineAndChange(version(11, amount, "supported"));
        assertThat(events).hasSize(expected);
        assertThat(observations.get(301400L).comparisonHistoryId()).isEqualTo(11);
    }

    @Test
    void eventContainsFrozenEvidenceAndNormalizedMicrosecondTimes() {
        baselineAndChange(version(11, "90000", "limited"));
        AlertOutboxEvent event = events.get(0);
        assertThat(event.getPropertyId()).isEqualTo(301400);
        assertThat(event.getSourceChangeId()).isEqualTo("valuation:301400:history:11");
        assertThat(event.getSavedSearchId()).isNull();
        assertThat(event.getPayload().path("oldValuation").path("historyId").asLong()).isEqualTo(10);
        assertThat(event.getPayload().path("newValuation").path("status").asText()).isEqualTo("limited");
        assertThat(event.getPayload().path("changePercent").decimalValue()).isEqualByComparingTo("-10");
        assertThat(event.getPayload().path("changeAmount").decimalValue()).isEqualByComparingTo("-10000");
        assertThat(event.getPayload().path("observationFinishedAt").asText()).isEqualTo("2026-11-13T10:00:00.720653Z");
        assertThat(event.getOccurredAt()).isEqualTo(Instant.parse("2026-11-13T10:00:00.720653Z"));
    }

    @Test
    void everyPublishedVersionIsComparedEvenIfPriceRecoversBeforeTheNextEvaluation() {
        service.evaluate(rule, state);
        Version old = current;
        current = version(12, "110000", "supported");
        when(history.version(301400, 10)).thenReturn(old);
        when(history.after(301400, 10, 100)).thenReturn(List.of(version(11, "90000", "supported"), current));
        service.evaluate(rule, state);
        assertThat(events).hasSize(2);
        assertThat(events.get(1).getPayload().path("oldValuation").path("historyId").asLong()).isEqualTo(11);
        when(history.after(301400, 12, 100)).thenReturn(List.of());
        service.evaluate(rule, state);
        assertThat(events).hasSize(2);
    }

    @Test
    void unsupportedGapRebaselinesRatherThanComparingAcrossMissingEvidence() {
        service.evaluate(rule, state);
        Version old = current;
        current = version(13, "130000", "supported");
        when(history.version(301400, 10)).thenReturn(old);
        when(history.after(301400, 10, 100)).thenReturn(List.of(version(11, "0", "unsupported"), current));
        service.evaluate(rule, state);
        assertThat(events).isEmpty();
        assertThat(observations.get(301400L).comparisonHistoryId()).isEqualTo(13);
    }

    @Test
    void unsupportedOrMissingCurrentEstimateDoesNotReuseASupportedPastValue() {
        service.evaluate(rule, state);
        when(history.latest(301400)).thenReturn(new Latest(current, 10, false));
        service.evaluate(rule, state);
        assertThat(events).isEmpty();
        assertThat(observations.get(301400L).comparisonHistoryId()).isNull();
    }

    @Test
    void boundedVersionPageResumesSamePropertyWithoutLosingCursorOrDuplicatingEvents() {
        service.evaluate(rule, state);
        Version old = current;
        current = version(110, "100000", "supported");
        when(history.version(301400, 10)).thenReturn(old);
        List<Version> versions = new ArrayList<>();
        for (int i = 11; i <= 110; i++)
            versions.add(version(i, "100000", "supported"));
        when(history.after(301400, 10, 100)).thenReturn(versions);
        service.evaluate(rule, state);
        assertThat(state.isRunning()).isTrue();
        assertThat(state.getCursorId()).isZero();
        assertThat(observations.get(301400L).cursorHistoryId()).isEqualTo(110);
        when(history.version(301400, 110)).thenReturn(current);
        when(history.after(301400, 110, 100)).thenReturn(List.of());
        service.evaluate(rule, state);
        assertThat(state.isRunning()).isFalse();
        assertThat(events).isEmpty();
    }

    @Test
    void savedSearchUsesMembershipAndNewlyEnteringPropertiesAreSilentlyBaselined() {
        SavedSearch saved = new SavedSearch();
        saved.setId(2L);
        saved.setUser(rule.getUser());
        rule.setProperty(null);
        rule.setSavedSearch(saved);
        SavedSearchFilters filters = new SavedSearchFilters();
        when(definitions.resolve(rule)).thenReturn(new AlertSearchDefinitionDTO(filters, "filters-hash", "stamp"));
        when(search.findMatchingIds(filters, 0, 100)).thenReturn(List.of(301400L));
        service.evaluate(rule, state);
        current = version(11, "90000", "supported");
        when(search.findMatchingIds(filters, 0, 100)).thenReturn(List.of(301401L));
        service.evaluate(rule, state);
        assertThat(events).isEmpty();
        verify(history, times(2)).retainGeneration(eq(1L), anyLong());
    }

    @Test
    void previouslyPersistedPublicationEventIsNotRepeated() {
        when(outbox.existsByRuleIdAndSourceChangeId(1L, "valuation:301400:history:11")).thenReturn(true);
        baselineAndChange(version(11, "90000", "supported"));
        assertThat(events).isEmpty();
        assertThat(observations.get(301400L).cursorHistoryId()).isEqualTo(11);
    }

    private void baselineAndChange(Version next) {
        service.evaluate(rule, state);
        Version old = current;
        current = next;
        when(history.version(301400, 10)).thenReturn(old);
        when(history.after(301400, 10, 100)).thenReturn(List.of(next));
        service.evaluate(rule, state);
    }

    private Version version(long id, String amount, String status) {
        return new Version(id, "PUBLISHED", "run-" + id, "v17+v18", LocalDate.of(2026, 11, 13), status,
                new BigDecimal(amount),
                Instant.parse("2026-11-13T09:00:00Z").plusSeconds(id));
    }
}
