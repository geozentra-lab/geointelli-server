package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.dto.AlertEventEnvelopeDTO;
import com.geointelli.ai.property.service.entity.*;
import com.geointelli.ai.property.service.repository.*;
import com.geointelli.ai.property.service.service.AlertEventPublisher;
import com.geointelli.ai.property.service.service.AlertSearchDefinitionService;
import com.geointelli.ai.property.service.service.PropertySearchService;

/**
 * Real migrations, publication observations, transactional outbox and publisher
 * bytes on a disposable database.
 */
@EnabledIfSystemProperty(named = "valuation.test.postgres.url", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/scrum151_producer_test")
class ValuationAlertDatabaseTest {
    static final String SCHEMA = "valuation_alert_" + UUID.randomUUID().toString().replace("-", "");
    static JdbcTemplate jdbc;
    static TransactionTemplate transactions;
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final Clock clock = Clock.fixed(Instant.parse("2026-11-13T10:00:00.720653200Z"), ZoneOffset.UTC);
    final AlertRule rule = new AlertRule();
    final AlertEvaluationState state = new AlertEvaluationState();
    final AlertMonitoringProperties settings = new AlertMonitoringProperties();
    AlertOutboxRepository outbox;
    ValuationAlertEvaluationServiceImpl service;

    @BeforeAll
    static void schema() throws Exception {
        var source = new DriverManagerDataSource(System.getProperty("valuation.test.postgres.url"), "notification_test",
                "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        source.setUrl(System.getProperty("valuation.test.postgres.url") + "?currentSchema=" + SCHEMA);
        jdbc.execute(
                "CREATE TABLE property_value_predictions(property_id bigint,run_id varchar(32),valuation_status varchar(12),predicted_price numeric)");
        jdbc.execute("CREATE TABLE users(id bigint PRIMARY KEY,keycloak_user_id varchar(255))");
        jdbc.execute("CREATE TABLE saved_searches(id bigint PRIMARY KEY,paused boolean DEFAULT false)");
        jdbc.execute(
                "CREATE TABLE alert_rules(id bigint PRIMARY KEY,user_id bigint,property_id bigint,saved_search_id bigint,paused boolean DEFAULT false,event_type varchar(30))");
        for (String file : List.of("V20__add_alert_monitoring_and_outbox.sql",
                "V33__add_valuation_history_and_refresh_requests.sql", "V35__add_valuation_change_alerts.sql"))
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/" + file)));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @AfterAll
    static void cleanup() {
        if (jdbc != null)
            jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @BeforeEach
    void setup() {
        jdbc.execute(
                "TRUNCATE alert_outbox,alert_valuation_observations,alert_evaluation_states,alert_rules,users,property_value_predictions,property_value_prediction_history RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users VALUES(7,'valuation-user')");
        jdbc.update(
                "INSERT INTO alert_rules(id,user_id,property_id,event_type) VALUES(1,7,301400,'VALUATION_CHANGED')");
        User user = new User();
        user.setId(7L);
        Property property = new Property();
        property.setId(301400L);
        rule.setId(1L);
        rule.setUser(user);
        rule.setProperty(property);
        rule.setEventType(AlertEventType.VALUATION_CHANGED);
        rule.setName("Value watch");
        state.setRuleId(1L);
        settings.setEnabled(true);
        settings.getPublisher().setEnabled(true);
        outbox = mock(AlertOutboxRepository.class);
        when(outbox.existsByRuleIdAndSourceChangeId(anyLong(), anyString())).thenAnswer(call -> jdbc.queryForObject(
                "SELECT count(*) FROM alert_outbox WHERE rule_id=? AND source_change_id=?", Integer.class,
                call.getArgument(0), call.getArgument(1)) > 0);
        when(outbox.save(any())).thenAnswer(call -> {
            AlertOutboxEvent event = call.getArgument(0);
            if (event.getId() == null)
                event.setId(jdbc.queryForObject(
                        """
                                INSERT INTO alert_outbox(rule_id,generation,property_id,recipient_keycloak_id,event_type,occurred_at,payload,next_attempt_at,source_change_id)
                                VALUES(?,?,?,?,?, ?,?::jsonb,?,?) RETURNING id
                                """,
                        Long.class, event.getRuleId(), event.getGeneration(), event.getPropertyId(),
                        event.getRecipientKeycloakId(),
                        event.getEventType().name(), Timestamp.from(event.getOccurredAt()),
                        event.getPayload().toString(),
                        Timestamp.from(event.getNextAttemptAt()), event.getSourceChangeId()));
            else
                jdbc.update(
                        "UPDATE alert_outbox SET status=?,attempts=?,last_error_code=?,next_attempt_at=? WHERE id=?",
                        event.getStatus(), event.getAttempts(), event.getLastErrorCode(),
                        Timestamp.from(event.getNextAttemptAt()), event.getId());
            return event;
        });
        service = new ValuationAlertEvaluationServiceImpl(new ValuationAlertRepository(jdbc), outbox,
                new AlertMonitoringRepository(jdbc), mock(AlertSearchDefinitionService.class),
                mock(PropertySearchService.class), settings, json, clock);
    }

    @Test
    void rollbackKeepsBothObservationAndOutboxUnchangedThenRetryPublishesExactlyOnce() {
        publish("100000", "supported");
        evaluate();
        long baseline = cursor();
        publish("90000", "supported");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            service.evaluate(rule, state);
            throw new IllegalStateException("Simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(cursor()).isEqualTo(baseline);
        assertThat(count()).isZero();
        evaluate();
        assertThat(count()).isEqualTo(1);
        evaluate();
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload->'oldValuation'->>'value' FROM alert_outbox", String.class))
                .isEqualTo("100000");
    }

    @Test
    void publicationRoundTripUsesStableVersionIdentityAndConsumerCompatibleTimes() throws Exception {
        publish("100000", "supported");
        evaluate();
        publish("90000", "limited");
        evaluate();
        AlertOutboxEvent loaded = load();
        var rules = mock(AlertRuleRepository.class);
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        when(outbox.findById(loaded.getId())).thenReturn(Optional.of(loaded));
        List<AlertEventEnvelopeDTO> published = new ArrayList<>();
        AlertEventPublisher publisher = published::add;
        var publishing = new AlertOutboxPublishingServiceImpl(new AlertMonitoringRepository(jdbc), outbox, rules,
                publisher, settings, clock);
        Boolean processed = transactions.execute(status -> publishing.processNext());
        assertThat(processed).isTrue();
        assertThat(published).hasSize(1);
        AlertEventEnvelopeDTO event = published.get(0);
        assertThat(event.sourceChangeId()).isEqualTo(loaded.getSourceChangeId());
        assertThat(event.occurredAt()).isEqualTo(Instant.parse(event.payload().path("observationFinishedAt").asText()));
        assertThat(event.occurredAt().getNano() % 1000).isZero();
        String destination = System.getProperty("valuation.producer.event");
        if (destination != null) {
            Path file = Path.of(destination);
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, json.writeValueAsBytes(event));
        }
    }

    @Test
    void competingEvaluationSessionsSkipTheSameRuleAndLegacyNewMatchUniquenessRemains() throws Exception {
        var source = jdbc.getDataSource();
        jdbc.update("INSERT INTO alert_evaluation_states(rule_id,next_evaluation_at) VALUES(1,'2020-01-01')");
        try (var first = source.getConnection(); var second = source.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            var a = new AlertMonitoringRepository(new JdbcTemplate(new SingleConnectionDataSource(first, true)));
            var b = new AlertMonitoringRepository(new JdbcTemplate(new SingleConnectionDataSource(second, true)));
            assertThat(a.lockNextDueRule(clock.instant())).isEqualTo(1);
            assertThat(b.lockNextDueRule(clock.instant())).isNull();
            first.rollback();
            assertThat(b.lockNextDueRule(clock.instant())).isEqualTo(1);
            second.rollback();
        }
        jdbc.update(
                "INSERT INTO alert_outbox(rule_id,generation,recipient_keycloak_id,event_type,occurred_at,payload,next_attempt_at) VALUES(1,9,'u','NEW_MATCH',now(),'{}',now())");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO alert_outbox(rule_id,generation,recipient_keycloak_id,event_type,occurred_at,payload,next_attempt_at) VALUES(1,9,'u','NEW_MATCH',now(),'{}',now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void criteriaResetInvalidatesBaselineAndCancelsOutdatedPendingValuationEvents() {
        publish("100000", "supported");
        evaluate();
        publish("90000", "supported");
        evaluate();
        jdbc.update("INSERT INTO alert_evaluation_states(rule_id,baseline_generation,baseline_hash) VALUES(1,1,'old')");
        transactions.executeWithoutResult(status -> new AlertMonitoringRepository(jdbc).resetValuationBaseline(1));
        assertThat(jdbc.queryForObject("SELECT status FROM alert_outbox", String.class)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT baseline_generation FROM alert_evaluation_states WHERE rule_id=1",
                Long.class)).isNull();
        assertThat(
                jdbc.queryForObject("SELECT baseline_hash FROM alert_evaluation_states WHERE rule_id=1", String.class))
                .isNull();
    }

    private void evaluate() {
        transactions.executeWithoutResult(status -> service.evaluate(rule, state));
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM alert_outbox", Integer.class);
    }

    private long cursor() {
        return jdbc.queryForObject(
                "SELECT cursor_history_id FROM alert_valuation_observations WHERE rule_id=1 AND property_id=301400",
                Long.class);
    }

    private void publish(String amount, String status) {
        String run = "test-" + UUID.randomUUID().toString().substring(0, 20);
        jdbc.update(
                """
                        INSERT INTO property_value_prediction_history(property_id,run_id,provenance,predicted_price,valuation_status,loaded_at,row_sha256)
                        VALUES(301400,?,'PUBLISHED',?::numeric,?,?,repeat('a',64))
                        """,
                run, amount, status, Timestamp.from(clock.instant().minusSeconds(3600).plusSeconds(
                        jdbc.queryForObject("SELECT count(*) FROM property_value_prediction_history", Long.class))));
        jdbc.update("DELETE FROM property_value_predictions");
        jdbc.update("INSERT INTO property_value_predictions VALUES(301400,?,?,?::numeric)", run, status, amount);
    }

    private AlertOutboxEvent load() throws Exception {
        var row = jdbc.queryForMap("SELECT * FROM alert_outbox LIMIT 1");
        AlertOutboxEvent value = new AlertOutboxEvent();
        value.setId((Long) row.get("id"));
        value.setRuleId(1L);
        value.setGeneration(((Number) row.get("generation")).longValue());
        value.setPropertyId(301400L);
        value.setRecipientKeycloakId((String) row.get("recipient_keycloak_id"));
        value.setEventType(AlertEventType.VALUATION_CHANGED);
        value.setSourceChangeId((String) row.get("source_change_id"));
        value.setOccurredAt(((Timestamp) row.get("occurred_at")).toInstant());
        value.setNextAttemptAt(((Timestamp) row.get("next_attempt_at")).toInstant());
        value.setPayload(json.readTree(row.get("payload").toString()));
        return value;
    }
}
