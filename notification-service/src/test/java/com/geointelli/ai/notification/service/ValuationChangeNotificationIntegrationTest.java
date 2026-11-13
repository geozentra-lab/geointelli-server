package com.geointelli.ai.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.geointelli.ai.notification.service.service.AlertEventDecoderService;
import com.geointelli.ai.notification.service.service.AlertEventConsumptionService;
import com.geointelli.ai.notification.service.repository.NotificationRepository;
import com.geointelli.ai.notification.service.repository.EmailQueueRepository;
import com.geointelli.ai.notification.service.exception.InvalidAlertEventException;

/**
 * Opt-in real database delivery; no Rabbit listener or external email sending
 * is enabled.
 */
@EnabledIfSystemProperty(named = "valuation.notification.test.url", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/scrum151_notifications_test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false", "management.health.rabbit.enabled=false",
        "notifications.email.enabled=false", "spring.datasource.hikari.data-source-properties.sslmode=disable" })
class ValuationChangeNotificationIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("valuation.notification.test.url"));
        registry.add("spring.datasource.username",
                () -> System.getProperty("valuation.notification.test.user", "notification_test"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("VALUATION_TEST_DB_PASSWORD", ""));
    }

    @Autowired
    AlertEventDecoderService decoder;
    @Autowired
    AlertEventConsumptionService consumption;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EmailQueueRepository emailQueue;

    @BeforeEach
    void clear() {
        jdbc.execute(
                "TRUNCATE notification_email_queue,notification_email_deliveries,notifications,consumed_alert_events,notification_preferences RESTART IDENTITY");
    }

    private byte[] bytes() throws Exception {
        return ValuationChangeDecoderTest.fixture().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void storesOneNotificationAndDuplicateDeliveryDoesNotCreateAnother() throws Exception {
        var event = decoder.decode(bytes());
        assertTrue(consumption.consume(event));
        assertFalse(consumption.consume(event));
        var notification = notifications.findByEventId("property-alert-151").orElseThrow();
        assertEquals("VALUATION_CHANGED", notification.getEventType());
        assertEquals(301400L, notification.getPropertyId());
        assertTrue(notification.getMessage().contains("not a sale price"));
        assertTrue(notification.getMessage().contains("limited support"));
        assertEquals(1, notifications.count());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM consumed_alert_events", Integer.class));
    }

    @Test
    void valuationOptOutIsIndependentOfNewMatchAndSuppressesDelivery() throws Exception {
        jdbc.update(
                "INSERT INTO notification_preferences(recipient_keycloak_id,updated_at,new_match_enabled,valuation_changed_enabled) VALUES('valuation-user',now(),true,false)");
        assertTrue(consumption.consume(decoder.decode(bytes())));
        assertEquals(0, notifications.count());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM consumed_alert_events", Integer.class));
    }

    @Test
    void sameEventIdWithDifferentContentIsRejectedWithoutAnotherNotification() throws Exception {
        consumption.consume(decoder.decode(bytes()));
        byte[] changed = ValuationChangeDecoderTest.fixture().replace("Valuation drop", "Changed rule name")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(InvalidAlertEventException.class, () -> consumption.consume(decoder.decode(changed)));
        assertEquals(1, notifications.count());
    }

    @Test
    void emailQueueRecognizesValuationPreferenceAndRetainsEventMessage() throws Exception {
        jdbc.update(
                "INSERT INTO notification_preferences(recipient_keycloak_id,updated_at,email_enabled,email_address,next_email_at,new_match_enabled,valuation_changed_enabled) VALUES('valuation-user',now(),true,'test@example.com',now(),false,true)");
        consumption.consume(decoder.decode(bytes()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_email_queue", Integer.class));
        var items = emailQueue.pending("valuation-user", 10);
        assertEquals(1, items.size());
        assertEquals("Valuation drop", items.get(0).ruleName());
        assertTrue(items.get(0).message().contains("decreased"));
    }

    @Test
    void acceptsActualDatabaseRoundTrippedProducerBytesWhenSupplied() throws Exception {
        String file = System.getProperty("valuation.producer.event");
        if (file == null) {
            assertNotNull(decoder.decode(bytes()).valuationPayload());
            return;
        }
        var event = decoder.decode(Files.readAllBytes(Path.of(file)));
        assertTrue(consumption.consume(event));
        assertFalse(consumption.consume(event));
        assertEquals(1, notifications.count());
    }
}
