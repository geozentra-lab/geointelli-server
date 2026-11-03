package com.geointelli.ai.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.geointelli.ai.notification.service.config.NotificationJsonConfig;
import com.geointelli.ai.notification.service.exception.InvalidAlertEventException;
import com.geointelli.ai.notification.service.service.AlertEventDecoderService;
import com.geointelli.ai.notification.service.service.AlertEventConsumptionService;
import tools.jackson.databind.node.ObjectNode;

/** Receives the producer's actual PostgreSQL-reloaded RabbitMQ event twice. */
@EnabledIfSystemProperty(named="alert.precision.integration",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.profiles.active=precision-test",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:55442/precision_consumer",
    "spring.datasource.username=precision_test","spring.datasource.password=",
    "spring.datasource.hikari.data-source-properties.sslmode=disable",
    "spring.rabbitmq.host=127.0.0.1","spring.rabbitmq.port=5679",
    "spring.rabbitmq.username=guest","spring.rabbitmq.password=guest",
    "spring.rabbitmq.listener.simple.auto-startup=true",
    "notifications.exchange=precision.alerts","notifications.queue=precision.notifications",
    "notifications.dead-letter-exchange=precision.dead",
    "notifications.dead-letter-queue=precision.notifications.dlq"})
class AlertTimestampDeliveryIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired AlertEventDecoderService decoder;
    @MockitoSpyBean AlertEventConsumptionService consumption;

    @Test void actualPublishedEventStoresOnceAndStillRejectsSubMicrosecondMismatch() throws Exception {
        byte[] body = Files.readAllBytes(Path.of(System.getProperty("alert.precision.event.file")));
        var decoded = decoder.decode(body);
        String eventId = decoded.envelope().eventId();
        assertEquals(Instant.parse("2026-10-30T01:38:04.720653Z"),decoded.envelope().occurredAt());
        assertEquals(decoded.envelope().occurredAt(),decoded.payload().observationFinishedAt());
        assertFalse(decoded.payload().observationStartedAt().isAfter(decoded.payload().observationFinishedAt()));
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        long deadline = System.nanoTime()+30_000_000_000L;
        boolean drained = false;
        while (System.nanoTime()<deadline) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE event_id=?",Long.class,eventId);
            var queue = admin.getQueueInfo("precision.notifications");
            if (count == 1 && queue != null && queue.getMessageCount() == 0) { drained = true; break; }
            Thread.sleep(100);
        }
        assertTrue(drained,"The real consumer must store the queued producer event");
        // Send another explicit duplicate after initial storage and wait for delivery.
        rabbit.send("precision.alerts","alerts.v1",new Message(body));
        verify(consumption,timeout(10000).times(3)).consume(any());
        assertFalse(consumption.consume(decoded));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE event_id=?",Integer.class,eventId));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM consumed_alert_events WHERE event_id=?",Integer.class,eventId));
        assertEquals(0,admin.getQueueInfo("precision.notifications").getMessageCount());
        assertEquals(0,admin.getQueueInfo("precision.notifications.dlq").getMessageCount());
        var json = new NotificationJsonConfig().notificationEventMapper();
        ObjectNode inconsistent = (ObjectNode) json.readTree(body);
        ((ObjectNode)inconsistent.get("payload")).put("observationFinishedAt","2026-10-30T01:38:04.720653200Z");
        InvalidAlertEventException error = assertThrows(InvalidAlertEventException.class,
            () -> decoder.decode(json.writeValueAsBytes(inconsistent)));
        assertEquals("INCONSISTENT_NEW_MATCH_PAYLOAD",error.getMessage());
        ((ObjectNode)inconsistent.get("payload")).put("observationFinishedAt","2026-10-30T01:38:04.720653Z");
        ((ObjectNode)inconsistent.get("payload")).put("observationStartedAt","2026-10-30T01:38:04.720653200Z");
        assertThrows(InvalidAlertEventException.class,() -> decoder.decode(json.writeValueAsBytes(inconsistent)));
    }
}
