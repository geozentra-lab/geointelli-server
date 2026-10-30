package com.geointelli.ai.notification.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.geointelli.ai.notification.service.dto.DecodedAlertEventDTO;
import com.geointelli.ai.notification.service.exception.InvalidAlertEventException;
import com.geointelli.ai.notification.service.repository.NotificationRepository;
import com.geointelli.ai.notification.service.service.AlertEventConsumptionService;
import com.geointelli.ai.notification.service.service.AlertEventDecoderService;

/** Opt-in PostgreSQL tests: use an explicitly disposable loopback DB; no application .env credentials are loaded. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.rabbitmq.listener.simple.auto-startup=false","management.health.rabbit.enabled=false"})
@EnabledIfSystemProperty(named="notification.test.postgres.url",matches="jdbc:postgresql://127\\.0\\.0\\.1:.*")
class NotificationServiceApplicationTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getProperty("notification.test.postgres.url"));
        registry.add("spring.datasource.username",()->"notification_test");registry.add("spring.datasource.password",()->"");
        registry.add("spring.datasource.hikari.data-source-properties.sslmode",()->"disable");
    }
    @Autowired AlertEventConsumptionService consumption;
    @Autowired AlertEventDecoderService decoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean NotificationRepository notifications;
    @BeforeEach void clean() { jdbc.update("DELETE FROM notifications");jdbc.update("DELETE FROM consumed_alert_events"); }
    DecodedAlertEventDTO event() { return decoder.decode(EventTestFixtures.body()); }
    @Test void migratesValidatesSchemaAndSavesOneUnreadNotification() {
        assertTrue(consumption.consume(event()));assertFalse(consumption.consume(event()));
        var saved=notifications.findByEventId("property-alert-1").orElseThrow();
        assertEquals("test-keycloak-user",saved.getRecipientKeycloakId());assertNull(saved.getReadAt());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM consumed_alert_events",Integer.class));
        assertEquals("object",jdbc.queryForObject("SELECT jsonb_typeof(payload) FROM notifications",String.class));
        assertEquals("2",jdbc.queryForObject("SELECT payload->>'newMatchCount' FROM notifications",String.class));
    }
    @Test void concurrentInstancesCreateOnlyOneNotification() throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(4);
        try {
            var decoded=event();List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<4;i++)results.add(workers.submit(()->consumption.consume(decoded)));
            int created=0;for(Future<Boolean> result:results)if(result.get(20,TimeUnit.SECONDS))created++;
            assertEquals(1,created);assertEquals(1,notifications.count());
        } finally { workers.shutdownNow(); }
    }
    @Test void failedNotificationWriteRollsBackReceiptAndCanRetry() {
        doThrow(new DataAccessResourceFailureException("simulated failure")).when(notifications).saveAndFlush(any());
        assertThrows(DataAccessResourceFailureException.class,()->consumption.consume(event()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consumed_alert_events",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Integer.class));
        reset(notifications);assertTrue(consumption.consume(event()));assertEquals(1,notifications.count());
    }
    @Test void reusedEventIdCannotOverwriteRecipient() {
        consumption.consume(event());
        var changed=decoder.decode(EventTestFixtures.text().replace("test-keycloak-user","other-user").getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidAlertEventException.class,()->consumption.consume(changed));assertEquals(1,notifications.count());
        assertEquals("test-keycloak-user",notifications.findByEventId("property-alert-1").orElseThrow().getRecipientKeycloakId());
    }
    @Test void receiptRemainsAfterNotificationDeletion() {
        consumption.consume(event());var saved=notifications.findByEventId("property-alert-1").orElseThrow();
        notifications.deleteById(saved.getId());assertFalse(consumption.consume(event()));assertEquals(0,notifications.count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM consumed_alert_events",Integer.class));
    }
}
