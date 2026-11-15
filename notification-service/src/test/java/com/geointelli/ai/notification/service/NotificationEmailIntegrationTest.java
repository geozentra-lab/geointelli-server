package com.geointelli.ai.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.notification.service.dto.NotificationPreferencesRequest;
import com.geointelli.ai.notification.service.dto.NotificationReadRequest;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO.Outcome;
import com.geointelli.ai.notification.service.entity.EmailCadence;
import com.geointelli.ai.notification.service.repository.NotificationRepository;
import com.geointelli.ai.notification.service.service.AlertEventConsumptionService;
import com.geointelli.ai.notification.service.service.AlertEventDecoderService;
import com.geointelli.ai.notification.service.service.EmailDeliveryStateService;
import com.geointelli.ai.notification.service.service.EmailWorkerService;
import com.geointelli.ai.notification.service.service.NotificationPreferenceService;
import com.geointelli.ai.notification.service.service.NotificationService;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.TooManyRequestsException;

/**
 * Opt-in real PostgreSQL tests. SES is mocked: no external emails or AWS
 * credentials are used.
 */
@EnabledIfSystemProperty(named = "email.test.postgres.url", matches = "jdbc:postgresql://127\\.0\\.0\\.1:.*")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false", "management.health.rabbit.enabled=false",
        "notifications.email.enabled=true", "notifications.email.from-address=sender@example.com",
        "notifications.email.poll-delay-millis=3600000", "notifications.email.max-digest-items=2" })
class NotificationEmailIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getProperty("email.test.postgres.url"));
        properties.add("spring.datasource.username", () -> "notification_test");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.hikari.data-source-properties.sslmode", () -> "disable");
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    NotificationPreferenceService preferences;
    @Autowired
    AlertEventConsumptionService consumption;
    @Autowired
    AlertEventDecoderService decoder;
    @Autowired
    NotificationService inbox;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    EmailWorkerService worker;
    @MockitoSpyBean
    EmailDeliveryStateService state;
    @MockitoBean(name = "notificationClock")
    Clock clock;
    @MockitoBean
    SesV2Client ses;
    private final Instant now = Instant.parse("2026-10-30T09:00:00Z");

    @BeforeEach
    void setup() {
        clear();
        when(clock.instant()).thenReturn(now);
        authenticate("test-keycloak-user", "owner@example.com", true);
        when(ses.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("ses-accepted").build());
    }

    @AfterEach
    void clean() {
        clear();
        SecurityContextHolder.clearContext();
    }

    private void clear() {
        jdbc.execute(
                "TRUNCATE notification_email_queue,notification_email_deliveries,notifications,consumed_alert_events,notification_preferences RESTART IDENTITY");
    }

    private void authenticate(String subject, String email, boolean verified) {
        var token = Jwt.withTokenValue("test").header("alg", "RS256").subject(subject).claim("email", email)
                .claim("email_verified", verified).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token, List.of()));
    }

    private NotificationPreferencesRequest choices(boolean inApp, boolean email, EmailCadence cadence) {
        return new NotificationPreferencesRequest(inApp, email, Set.of("NEW_MATCH"), cadence);
    }

    private void event(int id) {
        String text = EventTestFixtures.text().replace("property-alert-1", "property-alert-" + id);
        consumption.consume(decoder.decode(text.getBytes(StandardCharsets.UTF_8)));
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM notification_email_deliveries ORDER BY id DESC LIMIT 1",
                String.class);
    }

    @Test
    void defaultsPreserveInboxWithoutEnablingEmailOrCreatingRows() {
        var defaults = preferences.get();
        assertTrue(defaults.inAppEnabled());
        assertFalse(defaults.emailEnabled());
        assertEquals(Set.of("NEW_MATCH", "CONTACT_REQUEST", "VALUATION_CHANGED"), defaults.enabledEventTypes());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_preferences", Integer.class));
        event(1);
        assertEquals(1, inbox.list(0, 20).totalElements());
        assertFalse(worker.processNext());
        verifyNoInteractions(ses);
    }

    @Test
    void emailOptInRequiresVerifiedTokenEmailAndRemainsOwnerScoped() {
        authenticate("test-keycloak-user", "owner@example.com", false);
        assertThrows(ResponseStatusException.class, () -> preferences.update(choices(true, true, EmailCadence.DAILY)));
        authenticate("test-keycloak-user", "owner@example.com", true);
        preferences.update(choices(true, true, EmailCadence.DAILY));
        authenticate("other-user", "other@example.com", true);
        assertFalse(preferences.get().emailEnabled());
    }

    @Test
    void duplicateEventCreatesOneQueueItemAndAcceptedEmailIsNotSentAgain() {
        preferences.update(choices(true, true, EmailCadence.IMMEDIATE));
        event(1);
        event(1);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_email_queue", Integer.class));
        assertTrue(worker.processNext());
        assertEquals("ACCEPTED", status());
        assertFalse(worker.processNext());
        assertEquals("ses-accepted",
                jdbc.queryForObject("SELECT provider_message_id FROM notification_email_deliveries", String.class));
        verify(ses, times(1)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void sendingStateIsCommittedBeforeContactingSes() {
        preferences.update(choices(true, true, EmailCadence.IMMEDIATE));
        event(1);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenAnswer(call -> {
            assertEquals("SENDING", status());
            return SendEmailResponse.builder().messageId("committed").build();
        });
        assertTrue(worker.processNext());
        assertEquals("ACCEPTED", status());
    }

    @Test
    void emailOnlyEventsAreHiddenAndCannotBeMarkedReadThroughTheInbox() {
        preferences.update(choices(false, true, EmailCadence.IMMEDIATE));
        event(1);
        assertEquals(0, inbox.list(0, 20).totalElements());
        long id = notifications.findByEventId("property-alert-1").orElseThrow().getId();
        assertThrows(ResponseStatusException.class, () -> inbox.markRead(id, new NotificationReadRequest(true)));
        assertTrue(worker.processNext());
        assertEquals("ACCEPTED", status());
    }

    @Test
    void eventOptOutRetainsReceiptButDoesNotCreateAnyNotification() {
        preferences.update(new NotificationPreferencesRequest(true, true, Set.of(), EmailCadence.IMMEDIATE));
        event(1);
        assertEquals(0, notifications.count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM consumed_alert_events", Integer.class));
        assertFalse(worker.processNext());
        verifyNoInteractions(ses);
    }

    @Test
    void pendingEmailIsSuppressedWhenUserOptsOut() {
        preferences.update(choices(true, true, EmailCadence.IMMEDIATE));
        event(1);
        preferences.update(choices(true, false, EmailCadence.IMMEDIATE));
        assertFalse(worker.processNext());
        verifyNoInteractions(ses);
        assertEquals("SUPPRESSED", jdbc.queryForObject("SELECT status FROM notification_email_queue", String.class));
    }

    @Test
    void bothChannelsDisabledKeepOldInboxHistoryButDoNotAddNewItems() {
        event(1);
        preferences.update(choices(false, false, EmailCadence.DAILY));
        event(2);
        assertEquals(1, inbox.list(0, 20).totalElements());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM consumed_alert_events", Integer.class));
        verifyNoInteractions(ses);
    }

    @Test
    void changingVerifiedEmailCancelsOldRetryBatch() {
        preferences.update(choices(true, true, EmailCadence.IMMEDIATE));
        event(1);
        when(ses.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(TooManyRequestsException.builder().statusCode(429).build());
        worker.processNext();
        assertEquals("RETRY", status());
        authenticate("test-keycloak-user", "new-address@example.com", true);
        preferences.update(choices(true, true, EmailCadence.IMMEDIATE));
        assertEquals("SUPPRESSED", status());
        when(clock.instant()).thenReturn(now.plusSeconds(1000));
        assertFalse(worker.processNext());
        verify(ses, times(1)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void lateRejectionCannotRetryToAnOldEmailAddress() {
        preferences.update(choices(true, true, EmailCadence.IMMEDIATE));
        event(1);
        var dispatch = state.prepareNext();
        assertNotNull(dispatch);
        authenticate("test-keycloak-user", "new-address@example.com", true);
        preferences.update(choices(true, true, EmailCadence.IMMEDIATE));
        state.finish(dispatch, new EmailSendResultDTO(Outcome.RETRYABLE_REJECTION, null, "SES_THROTTLED"));
        when(clock.instant()).thenReturn(now.plusSeconds(1000));
        assertFalse(worker.processNext());
        assertEquals("SUPPRESSED", status());
        verifyNoInteractions(ses);
    }

    @Test
    void dailyDigestWaitsForUtcMidnightAndBoundsItsItems() {
        var original = preferences.update(choices(true, true, EmailCadence.DAILY));
        assertEquals(Instant.parse("2026-10-31T00:00:00Z"), original.nextEmailAt());
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertEquals(original.nextEmailAt(), preferences.update(choices(true, true, EmailCadence.DAILY)).nextEmailAt());
        event(1);
        event(2);
        event(3);
        assertFalse(worker.processNext());
        when(clock.instant()).thenReturn(Instant.parse("2026-10-31T00:00:00Z"));
        assertTrue(worker.processNext());
        assertFalse(worker.processNext());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM notification_email_queue WHERE delivery_id=1",
                Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_email_queue WHERE status='PENDING'",
                Integer.class));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-01T00:00:00Z"));
        assertTrue(worker.processNext());
        verify(ses, times(2)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void weeklyDigestWaitsForMondayUtc() {
        preferences.update(choices(true, true, EmailCadence.WEEKLY));
        event(1);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-01T23:59:59Z"));
        assertFalse(worker.processNext());
        when(clock.instant()).thenReturn(Instant.parse("2026-11-02T00:00:00Z"));
        assertTrue(worker.processNext());
    }

    @Test
    void explicitThrottlingRetriesAfterBackoffAndStopsAtTheLimit() {
        preferences.update(choices(true, true, EmailCadence.IMMEDIATE));
        event(1);
        when(ses.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(TooManyRequestsException.builder().statusCode(429).build());
        assertTrue(worker.processNext());
        assertEquals("RETRY", status());
        assertFalse(worker.processNext());
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertTrue(worker.processNext());
        when(clock.instant()).thenReturn(now.plusSeconds(180));
        assertTrue(worker.processNext());
        assertEquals("FAILED", status());
        assertFalse(worker.processNext());
        verify(ses, times(3)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void timeoutIsUnknownAndNeverAutomaticallyResent() {
        preferences.update(choices(true, true, EmailCadence.IMMEDIATE));
        event(1);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenThrow(SdkClientException.create("timeout"));
        worker.processNext();
        assertEquals("UNKNOWN", status());
        when(clock.instant()).thenReturn(now.plusSeconds(86400));
        assertFalse(worker.processNext());
        verify(ses, times(1)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void crashAfterProviderAcceptanceDoesNotBlindlyResend() {
        preferences.update(choices(true, true, EmailCadence.IMMEDIATE));
        event(1);
        doThrow(new DataAccessResourceFailureException("simulated commit failure")).when(state).finish(any(), any());
        assertThrows(DataAccessResourceFailureException.class, worker::processNext);
        assertEquals("SENDING", status());
        reset(state);
        when(clock.instant()).thenReturn(now.plusSeconds(121));
        assertFalse(worker.processNext());
        assertEquals("UNKNOWN", status());
        verify(ses, times(1)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void twoInstancesCannotClaimTheSameQueuedEmail() throws Exception {
        preferences.update(choices(true, true, EmailCadence.IMMEDIATE));
        event(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++)
                results.add(pool.submit(worker::processNext));
            int dispatched = 0;
            for (var result : results)
                if (result.get(20, TimeUnit.SECONDS))
                    dispatched++;
            assertEquals(1, dispatched);
            verify(ses, times(1)).sendEmail(any(SendEmailRequest.class));
        } finally {
            pool.shutdownNow();
        }
    }

    private void contactEvent() throws Exception {
        try (var stream = getClass().getResourceAsStream("/contact-request-v1.json")) {
            consumption.consume(decoder.decode(stream.readAllBytes()));
        }
    }

    @Test
    void contactOnlyEmailDispatchesEvenWhenSavedSearchAlertsAreDisabled() throws Exception {
        authenticate("professional", "professional@example.com", true);
        preferences.update(
                new NotificationPreferencesRequest(true, true, Set.of("CONTACT_REQUEST"), EmailCadence.IMMEDIATE));
        contactEvent();
        contactEvent();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_email_queue", Integer.class));
        assertTrue(worker.processNext());
        assertEquals("ACCEPTED", status());
        assertFalse(worker.processNext());
        verify(ses, times(1)).sendEmail(argThat((SendEmailRequest request) -> request.content().simple().body().text()
                .data().contains("New property contact request")));
    }

    @Test
    void optingOutOfSavedSearchesDoesNotSuppressPendingContactEmail() throws Exception {
        authenticate("professional", "professional@example.com", true);
        preferences.update(new NotificationPreferencesRequest(true, true, Set.of("NEW_MATCH", "CONTACT_REQUEST"),
                EmailCadence.IMMEDIATE));
        contactEvent();
        consumption.consume(decoder.decode(EventTestFixtures.text().replace("test-keycloak-user", "professional")
                .getBytes(StandardCharsets.UTF_8)));
        preferences.update(
                new NotificationPreferencesRequest(true, true, Set.of("CONTACT_REQUEST"), EmailCadence.IMMEDIATE));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_email_queue WHERE status='SUPPRESSED'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_email_queue WHERE status='PENDING'",
                Integer.class));
        assertTrue(worker.processNext());
        assertEquals("ACCEPTED", status());
        verify(ses, times(1)).sendEmail(argThat((SendEmailRequest request) -> request.content().simple().body().text()
                .data().contains("New property contact request")));
    }

    @Test
    void optingOutOfContactsRetainsReceiptWithoutCreatingNotification() throws Exception {
        authenticate("professional", "professional@example.com", true);
        preferences.update(new NotificationPreferencesRequest(true, true, Set.of("NEW_MATCH"), EmailCadence.IMMEDIATE));
        contactEvent();
        assertEquals(0, notifications.count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM consumed_alert_events", Integer.class));
        assertFalse(worker.processNext());
        verifyNoInteractions(ses);
    }
}
