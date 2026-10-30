package com.geointelli.ai.notification.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.geointelli.ai.notification.service.repository.NotificationRepository;

/** Opt-in end-to-end delivery against a disposable broker and PostgreSQL database, never the user's queues. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "notifications.retry-initial-millis=5","notifications.retry-max-millis=10",
    "spring.rabbitmq.listener.simple.concurrency=1","spring.rabbitmq.listener.simple.max-concurrency=1"})
@EnabledIfSystemProperty(named="notification.test.rabbit.port",matches="[0-9]+")
class RabbitDeliveryTests {
    static final String MAIN="geozentra.notifications.alerts", DLQ="geozentra.notifications.alerts.dlq";
    @DynamicPropertySource static void isolatedInfrastructure(DynamicPropertyRegistry registry) {
        String url=System.getProperty("notification.test.postgres.url");
        if (url==null || !url.startsWith("jdbc:postgresql://127.0.0.1:")) throw new IllegalStateException("Disposable PostgreSQL URL required");
        registry.add("spring.datasource.url",()->url);
        registry.add("spring.datasource.username",()->"notification_test");registry.add("spring.datasource.password",()->"");
        registry.add("spring.datasource.hikari.data-source-properties.sslmode",()->"disable");
        registry.add("spring.rabbitmq.host",()->"127.0.0.1");
        registry.add("spring.rabbitmq.port",()->System.getProperty("notification.test.rabbit.port"));
        registry.add("spring.rabbitmq.username",()->"guest");registry.add("spring.rabbitmq.password",()->"guest");
    }
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean NotificationRepository notifications;
    @BeforeEach void clean() {
        admin.purgeQueue(MAIN,false);admin.purgeQueue(DLQ,false);
        jdbc.update("DELETE FROM notifications");jdbc.update("DELETE FROM consumed_alert_events");
    }
    byte[] body(String id) { return EventTestFixtures.text().replace("property-alert-1",id).getBytes(StandardCharsets.UTF_8); }
    void publish(byte[] body) {
        MessageProperties properties=new MessageProperties();properties.setContentType("application/json");
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        rabbit.send("geozentra.alerts","alerts.v1",new Message(body,properties));
    }
    @Test void realListenerReceivesProducerJsonAndCommitsNotification() throws Exception {
        String id="live-"+UUID.randomUUID();publish(body(id));
        await(()->notifications.findByEventId(id).isPresent());
        assertEquals("test-keycloak-user",notifications.findByEventId(id).orElseThrow().getRecipientKeycloakId());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM consumed_alert_events",Integer.class));
    }
    @Test void unsupportedVersionIsRetainedInDeadLetterQueue() {
        byte[] invalid=new String(body("unsupported-"+UUID.randomUUID()),StandardCharsets.UTF_8)
            .replace("\"eventVersion\": 1","\"eventVersion\": 999").getBytes(StandardCharsets.UTF_8);
        publish(invalid);Message failed=rabbit.receive(DLQ,15000);assertNotNull(failed);
        assertArrayEquals(invalid,failed.getBody());
        assertEquals("UNSUPPORTED_SOURCE_OR_VERSION",failed.getMessageProperties().getHeader("failure-code"));
        assertEquals(3,((Number)failed.getMessageProperties().getHeader("processing-attempts")).intValue());
        assertEquals(0,notifications.count());
    }
    @Test void exhaustedDatabaseFailuresGoToDlqAndOriginalEventCanBeReplayed() throws Exception {
        String id="replay-"+UUID.randomUUID();byte[] original=body(id);
        doThrow(new DataAccessResourceFailureException("simulated offline database")).when(notifications).saveAndFlush(any());
        publish(original);Message failed=rabbit.receive(DLQ,15000);assertNotNull(failed);
        assertEquals("PROCESSING_FAILED",failed.getMessageProperties().getHeader("failure-code"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consumed_alert_events",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Integer.class));
        reset(notifications);publish(failed.getBody());await(()->notifications.findByEventId(id).isPresent());
        assertEquals(1,notifications.count());
    }
    void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+15_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(50);
        assertTrue(condition.getAsBoolean(),"Timed out waiting for a committed notification");
    }
}
