package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.dto.AlertSearchDefinitionDTO;
import com.geointelli.ai.property.service.dto.NewMatchPayloadDTO;
import com.geointelli.ai.property.service.dto.SavedSearchFilters;
import com.geointelli.ai.property.service.entity.AlertEvaluationState;
import com.geointelli.ai.property.service.entity.AlertEventType;
import com.geointelli.ai.property.service.entity.AlertOutboxEvent;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.AlertEvaluationStateRepository;
import com.geointelli.ai.property.service.repository.AlertMonitoringRepository;
import com.geointelli.ai.property.service.repository.AlertOutboxRepository;
import com.geointelli.ai.property.service.repository.AlertRuleRepository;
import com.geointelli.ai.property.service.service.AlertSearchDefinitionService;
import com.geointelli.ai.property.service.service.PropertySearchService;

/** Opt-in contract verification using only disposable PostgreSQL and RabbitMQ instances. */
@EnabledIfSystemProperty(named="alert.precision.integration", matches="true")
class AlertTimestampDeliveryTest {
    @Test void persistedCompletionSurvivesReloadAndRabbitPublication() throws Exception {
        String url = "jdbc:postgresql://127.0.0.1:55442/precision_producer?currentSchema=precision_contract";
        DriverManagerDataSource source = new DriverManagerDataSource(url,"precision_test","");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA IF EXISTS precision_contract CASCADE");
        jdbc.execute("CREATE SCHEMA precision_contract");
        jdbc.execute("CREATE TABLE saved_searches(id BIGINT PRIMARY KEY,paused BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE alert_rules(id BIGINT PRIMARY KEY,saved_search_id BIGINT,paused BOOLEAN DEFAULT FALSE)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V20__add_alert_monitoring_and_outbox.sql")).execute(source);
        jdbc.update("INSERT INTO saved_searches(id) VALUES(4)");
        jdbc.update("INSERT INTO alert_rules(id,saved_search_id) VALUES(1,4)");
        Instant precise = Instant.parse("2026-10-30T01:38:04.720653200Z");
        Instant expected = precise.truncatedTo(ChronoUnit.MICROS);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        Configuration configuration = new Configuration().addAnnotatedClass(AlertOutboxEvent.class);
        configuration.setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy());
        configuration.setProperty("hibernate.connection.url",url);
        configuration.setProperty("hibernate.connection.username","precision_test");
        configuration.setProperty("hibernate.hbm2ddl.auto","validate");
        try (SessionFactory factory = configuration.buildSessionFactory()) {
            // Reproduce the original bug through the same Hibernate mapping and PostgreSQL column.
            AlertOutboxEvent original = new AlertOutboxEvent();
            original.setRuleId(1L); original.setGeneration(0); original.setRecipientKeycloakId("precision-test-user");
            original.setEventType(AlertEventType.NEW_MATCH); original.setOccurredAt(precise); original.setNextAttemptAt(precise);
            original.setPayload(json.valueToTree(new NewMatchPayloadDTO("Original precision","a".repeat(64),
                1,1,List.of(90L),false,precise.minusSeconds(1),precise)));
            try (var session = factory.openSession()) {
                var transaction = session.beginTransaction(); session.persist(original); transaction.commit();
            }
            try (var session = factory.openSession()) {
                AlertOutboxEvent old = session.find(AlertOutboxEvent.class,original.getId());
                assertEquals(expected,old.getOccurredAt());
                assertEquals(precise,json.treeToValue(old.getPayload(),NewMatchPayloadDTO.class).observationFinishedAt());
                assertTrue(precise.isAfter(old.getOccurredAt()),"Unnormalized JSON reproduces the real rejection");
            }
            AlertRuleRepository rules = mock(AlertRuleRepository.class);
            AlertEvaluationStateRepository states = mock(AlertEvaluationStateRepository.class);
            AlertMonitoringRepository monitoring = mock(AlertMonitoringRepository.class);
            AlertOutboxRepository outbox = mock(AlertOutboxRepository.class);
            AlertSearchDefinitionService definitions = mock(AlertSearchDefinitionService.class);
            PropertySearchService search = mock(PropertySearchService.class);
            User user = new User(); user.setId(9L);
            SavedSearch saved = new SavedSearch(); saved.setId(4L);
            AlertRule rule = new AlertRule(); rule.setId(1L); rule.setUser(user); rule.setSavedSearch(saved);
            rule.setEventType(AlertEventType.NEW_MATCH); rule.setName("Precision verification");
            AlertEvaluationState state = new AlertEvaluationState(); state.setRuleId(1L);
            state.setGeneration(1); state.setBaselineGeneration(1L); state.setBaselineHash("a".repeat(64));
            SavedSearchFilters filters = new SavedSearchFilters();
            when(monitoring.lockNextDueRule(expected)).thenReturn(1L);
            when(rules.findById(1L)).thenReturn(Optional.of(rule));
            when(states.findById(1L)).thenReturn(Optional.of(state));
            when(definitions.resolve(rule)).thenReturn(new AlertSearchDefinitionDTO(filters,"a".repeat(64),"stamp"));
            when(search.findMatchingIds(eq(filters),anyLong(),anyInt())).thenReturn(List.of(90L));
            when(monitoring.countNew(1,2,1)).thenReturn(1L);
            when(monitoring.previewNew(1,2,1)).thenReturn(List.of(90L));
            when(monitoring.recipient(9)).thenReturn("precision-test-user");
            when(outbox.save(any())).thenAnswer(invocation -> {
                AlertOutboxEvent event = invocation.getArgument(0);
                try (var session = factory.openSession()) {
                    var transaction = session.beginTransaction(); session.persist(event); transaction.commit();
                }
                return event;
            });
            AlertMonitoringProperties settings = new AlertMonitoringProperties();
            settings.setEnabled(true); settings.getPublisher().setEnabled(true);
            settings.getPublisher().setExchange("precision.alerts");
            settings.getPublisher().setQueue("precision.notifications");
            Clock clock = Clock.fixed(precise,ZoneOffset.UTC);
            new AlertEvaluationServiceImpl(rules,states,monitoring,outbox,definitions,search,settings,json,clock).processNextBatch();
            long id = jdbc.queryForObject("SELECT id FROM alert_outbox WHERE generation=2",Long.class);
            AlertOutboxEvent reloaded;
            try (var session = factory.openSession()) { reloaded = session.find(AlertOutboxEvent.class,id); }
            NewMatchPayloadDTO payload = json.treeToValue(reloaded.getPayload(),NewMatchPayloadDTO.class);
            assertEquals(expected,reloaded.getOccurredAt());
            assertEquals(expected,payload.observationFinishedAt());
            assertFalse(payload.observationStartedAt().isAfter(payload.observationFinishedAt()));
            assertEquals(expected,jdbc.queryForObject("SELECT occurred_at FROM alert_outbox WHERE generation=2",Timestamp.class).toInstant());
            CachingConnectionFactory connection = new CachingConnectionFactory("127.0.0.1",
                Integer.getInteger("alert.precision.rabbit.port",5679));
            connection.setPublisherConfirmType(ConfirmType.CORRELATED); connection.setPublisherReturns(true);
            try {
                RabbitAdmin admin = new RabbitAdmin(connection);
                TopicExchange exchange = new TopicExchange("precision.alerts",true,false);
                Queue queue = new Queue("precision.notifications",true);
                admin.declareExchange(exchange); admin.declareQueue(queue);
                admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(settings.getPublisher().getRoutingKey()));
                RabbitTemplate rabbit = new RabbitTemplate(connection); rabbit.setMandatory(true);
                when(monitoring.lockNextOutbox(precise)).thenReturn(id);
                when(outbox.findById(id)).thenReturn(Optional.of(reloaded));
                // Publishing reloads a detached event, as a later outbox worker would.
                doAnswer(invocation -> invocation.getArgument(0)).when(outbox).save(any());
                new AlertOutboxPublishingServiceImpl(monitoring,outbox,rules,
                    new RabbitAlertEventPublisherImpl(rabbit,json,settings),settings,clock).processNext();
                assertEquals("SENT",reloaded.getStatus());
                Message published = rabbit.receive("precision.notifications",5000);
                assertNotNull(published);
                var envelope = json.readTree(published.getBody());
                assertEquals(expected,Instant.parse(envelope.get("occurredAt").asText()));
                assertEquals(expected,Instant.parse(envelope.get("payload").get("observationFinishedAt").asText()));
                assertEquals("property-alert-"+id,envelope.get("eventId").asText());
                Files.write(Path.of("target/alert-precision-event.json"),published.getBody());
                // Put two identical real broker deliveries back for the consumer verification.
                for (int delivery = 0; delivery < 2; delivery++) {
                    CorrelationData confirm = new CorrelationData("duplicate-"+delivery);
                    rabbit.send("precision.alerts",settings.getPublisher().getRoutingKey(),published,confirm);
                    assertTrue(confirm.getFuture().get(10,TimeUnit.SECONDS).isAck());
                    assertNull(confirm.getReturned());
                }
            } finally { connection.destroy(); }
        }
    }
}
