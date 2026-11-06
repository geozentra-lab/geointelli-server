package com.geointelli.ai.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.geointelli.ai.notification.service.dto.DecodedAlertEventDTO;
import com.geointelli.ai.notification.service.exception.InvalidAlertEventException;
import com.geointelli.ai.notification.service.service.AlertEventConsumptionService;
import com.geointelli.ai.notification.service.service.AlertEventDecoderService;
import com.geointelli.ai.notification.service.repository.NotificationRepository;

/** Flyway V3, actual receipt insertion and notification deduplication on a disposable PostgreSQL database. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.rabbitmq.listener.simple.auto-startup=false","management.health.rabbit.enabled=false",
    "notifications.email.enabled=false"})
@EnabledIfSystemProperty(named="notification.test.postgres.url",matches="jdbc:postgresql://127\\.0\\.0\\.1:.*")
class ContactRequestNotificationIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",()->System.getProperty("notification.test.postgres.url"));
        properties.add("spring.datasource.username",()->"notification_test");
        properties.add("spring.datasource.password",()->"");
        properties.add("spring.datasource.hikari.data-source-properties.sslmode",()->"disable");
    }
    @Autowired AlertEventDecoderService decoder;
    @Autowired AlertEventConsumptionService consumption;
    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM notification_email_queue");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM consumed_alert_events");
        jdbc.update("DELETE FROM notification_preferences WHERE recipient_keycloak_id IN ('professional','other')");
    }
    @AfterEach void clearEmailQueue() { jdbc.update("DELETE FROM notification_email_queue"); }
    private byte[] bytes() throws Exception {
        try(var input=getClass().getResourceAsStream("/contact-request-v1.json")) { return input.readAllBytes(); }
    }
    private DecodedAlertEventDTO event() throws Exception { return decoder.decode(bytes()); }
    @Test void storesOneNotificationAndDuplicateCreatesNothing() throws Exception {
        var event=event();
        assertTrue(consumption.consume(event));assertFalse(consumption.consume(event));
        var value=notifications.findByEventId(event.envelope().eventId()).orElseThrow();
        assertEquals("CONTACT_REQUEST",value.getEventType());assertNull(value.getAlertRuleId());
        assertEquals("professional",value.getRecipientKeycloakId());assertEquals(100L,value.getPropertyId());
        assertEquals(1,notifications.count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM consumed_alert_events",Integer.class));
    }
    @Test void concurrentConsumersStoreOneNotification() throws Exception {
        var event=event(); var pool=Executors.newFixedThreadPool(2);
        try {
            var tasks=pool.invokeAll(List.of(()->consumption.consume(event),()->consumption.consume(event)));
            int inserted=0;
            for(var result:tasks) if(Boolean.TRUE.equals(result.get(20,TimeUnit.SECONDS))) inserted++;
            assertEquals(1,inserted);assertEquals(1,notifications.count());
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test void cannotReuseEventIdToRedirectRecipient() throws Exception {
        consumption.consume(event());
        var changed=decoder.decode(new String(bytes(),StandardCharsets.UTF_8).replace("professional","other").getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidAlertEventException.class,()->consumption.consume(changed));
        assertEquals(1,notifications.count());
    }
    @Test void newMatchToggleDoesNotSuppressContactsButGlobalChannelsDo() throws Exception {
        consumption.consume(event());
        jdbc.update("UPDATE notification_preferences SET new_match_enabled=false WHERE recipient_keycloak_id='professional'");
        var second=decoder.decode(new String(bytes(),StandardCharsets.UTF_8).replace("property-contact-1","property-contact-2")
            .replace("contact-request:1","contact-request:2").replace("\"contactRequestId\": 1","\"contactRequestId\": 2").getBytes(StandardCharsets.UTF_8));
        assertTrue(consumption.consume(second));assertEquals(2,notifications.count());
        jdbc.update("UPDATE notification_preferences SET in_app_enabled=false,email_enabled=false WHERE recipient_keycloak_id='professional'");
        var third=decoder.decode(new String(bytes(),StandardCharsets.UTF_8).replace("property-contact-1","property-contact-3")
            .replace("contact-request:1","contact-request:3").replace("\"contactRequestId\": 1","\"contactRequestId\": 3").getBytes(StandardCharsets.UTF_8));
        assertTrue(consumption.consume(third));assertEquals(2,notifications.count());
    }
    @Test void emailQueueUsesContactTitleAndDoesNotDuplicate() throws Exception {
        consumption.consume(event());
        jdbc.update("UPDATE notification_preferences SET email_enabled=true,new_match_enabled=false,email_address='professional@example.com',next_email_at=NOW() WHERE recipient_keycloak_id='professional'");
        var second=decoder.decode(new String(bytes(),StandardCharsets.UTF_8).replace("property-contact-1","property-contact-2")
            .replace("contact-request:1","contact-request:2").replace("\"contactRequestId\": 1","\"contactRequestId\": 2").getBytes(StandardCharsets.UTF_8));
        assertTrue(consumption.consume(second));assertFalse(consumption.consume(second));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification_email_queue",Integer.class));
    }

    @Test void acceptsActualProducerPublishedBytesWhenSupplied() throws Exception {
        String path=System.getProperty("contact.producer.event");
        if(path==null) { assertNotNull(event().contactPayload()); return; }
        var produced=decoder.decode(Files.readAllBytes(Path.of(path)));
        assertTrue(consumption.consume(produced));assertFalse(consumption.consume(produced));
        assertEquals(1,notifications.count());
    }
}
