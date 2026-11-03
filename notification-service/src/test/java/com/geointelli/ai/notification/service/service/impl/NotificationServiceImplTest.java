package com.geointelli.ai.notification.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.notification.service.config.NotificationJsonConfig;
import com.geointelli.ai.notification.service.dto.NotificationReadRequest;
import com.geointelli.ai.notification.service.entity.Notification;
import com.geointelli.ai.notification.service.repository.NotificationRepository;
import com.geointelli.ai.notification.service.service.CurrentUserService;

/** Unit checks for owner-scoped inbox queries and read-status handling, without external services. */
class NotificationServiceImplTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final Instant now = Instant.parse("2026-10-30T01:38:04.720653200Z");
    private final Instant persistedNow = Instant.parse("2026-10-30T01:38:04.720653Z");
    private final NotificationServiceImpl service = new NotificationServiceImpl(notifications, currentUser,
        Clock.fixed(now, ZoneOffset.UTC), new NotificationJsonConfig().notificationEventMapper());

    @BeforeEach
    void identifyOwner() {
        when(currentUser.getSubject()).thenReturn("owner-a");
    }

    @Test
    void listsOnlyCurrentRecipientWithStableOrderingAndMapsThePayload() {
        Notification first = notification(3L, null);
        Notification second = notification(2L, persistedNow);
        PageRequest expected = PageRequest.of(0, 2,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        when(notifications.findByRecipientKeycloakId("owner-a", expected))
            .thenReturn(new PageImpl<>(List.of(first, second), expected, 5));

        var page = service.list(0, 2);

        assertEquals(List.of(3L, 2L), page.content().stream().map(item -> item.id()).toList());
        assertEquals(5, page.totalElements());
        assertEquals(3, page.totalPages());
        assertEquals(0, page.page());
        assertEquals(2, page.size());
        assertTrue(page.first());
        assertFalse(page.last());
        var item = page.content().get(0);
        assertEquals("NEW_MATCH", item.eventType());
        assertEquals("New matching properties", item.title());
        assertEquals("1 property now matches your saved search.", item.message());
        assertEquals(7L, item.alertRuleId());
        assertEquals(4L, item.savedSearchId());
        assertNull(item.propertyId());
        assertEquals(first.getOccurredAt(), item.occurredAt());
        assertEquals(first.getCreatedAt(), item.createdAt());
        assertTrue(item.payload().isObject());
        assertEquals(1, item.payload().get("newMatchCount").asInt());
        assertFalse(item.read());
        assertNull(item.readAt());
        assertTrue(page.content().get(1).read());
        assertEquals(persistedNow, page.content().get(1).readAt());
        verify(notifications).findByRecipientKeycloakId("owner-a", expected);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void emptyInboxReturnsZeroTotals() {
        when(notifications.findByRecipientKeycloakId(eq("owner-a"), any(Pageable.class)))
            .thenAnswer(call -> Page.empty(call.getArgument(1, Pageable.class)));

        var result = service.list(0, 20);

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
        assertTrue(result.first());
        assertTrue(result.last());
    }

    @Test
    void laterEmptyPageKeepsTheOwnedTotalAndNavigation() {
        PageRequest request = PageRequest.of(3, 2,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        when(notifications.findByRecipientKeycloakId("owner-a", request))
            .thenReturn(new PageImpl<>(List.of(), request, 3));

        var result = service.list(3, 2);

        assertTrue(result.content().isEmpty());
        assertEquals(3, result.totalElements());
        assertEquals(2, result.totalPages());
        assertFalse(result.first());
        assertTrue(result.last());
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,100"})
    void rejectsInvalidPaginationBeforeQuerying(int page, int size) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.list(page, size));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(notifications);
    }

    @Test
    void currentIdentityControlsTheRecipientQuery() {
        when(currentUser.getSubject()).thenReturn("owner-b");
        when(notifications.findByRecipientKeycloakId(eq("owner-b"), any(Pageable.class)))
            .thenReturn(Page.empty());

        service.list(0, 100);

        verify(notifications).findByRecipientKeycloakId(eq("owner-b"), any(Pageable.class));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void marksOwnedNotificationReadUsingDatabasePrecision() {
        when(notifications.markUnreadAsRead(3L, "owner-a", persistedNow)).thenReturn(1);
        when(notifications.findByIdAndRecipientKeycloakId(3L, "owner-a"))
            .thenReturn(Optional.of(notification(3L, persistedNow)));

        var result = service.markRead(3L, new NotificationReadRequest(true));

        assertTrue(result.read());
        assertEquals(persistedNow, result.readAt());
        verify(notifications).markUnreadAsRead(3L, "owner-a", persistedNow);
        verify(notifications).findByIdAndRecipientKeycloakId(3L, "owner-a");
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void alreadyReadNotificationKeepsItsOriginalTimeOnRepeatedCalls() {
        Instant original = Instant.parse("2026-10-29T10:00:00Z");
        when(notifications.markUnreadAsRead(3L, "owner-a", persistedNow)).thenReturn(0);
        when(notifications.findByIdAndRecipientKeycloakId(3L, "owner-a"))
            .thenReturn(Optional.of(notification(3L, original)));

        var first = service.markRead(3L, new NotificationReadRequest(true));
        var repeated = service.markRead(3L, new NotificationReadRequest(true));

        assertTrue(first.read());
        assertEquals(original, first.readAt());
        assertEquals(first, repeated);
        verify(notifications, times(2)).markUnreadAsRead(3L, "owner-a", persistedNow);
        verify(notifications, times(2)).findByIdAndRecipientKeycloakId(3L, "owner-a");
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void missingOrForeignNotificationReturns404WithoutAnUnscopedLookup() {
        when(notifications.findByIdAndRecipientKeycloakId(50L, "owner-a")).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.markRead(50L, new NotificationReadRequest(true)));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(notifications).markUnreadAsRead(50L, "owner-a", persistedNow);
        verify(notifications).findByIdAndRecipientKeycloakId(50L, "owner-a");
        verifyNoMoreInteractions(notifications);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidIdsBeforeUpdating(Long id) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.markRead(id, new NotificationReadRequest(true)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(notifications);
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void rejectsUnsupportedUpdatesBeforeTouchingPersistence(NotificationReadRequest request) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.markRead(3L, request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(notifications);
    }

    static Stream<Arguments> invalidUpdates() {
        return Stream.of(Arguments.of((NotificationReadRequest) null),
            Arguments.of(new NotificationReadRequest(null)), Arguments.of(new NotificationReadRequest(false)));
    }

    @Test
    void missingIdentityStopsBothOperationsBeforePersistence() {
        when(currentUser.getSubject()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertEquals(HttpStatus.UNAUTHORIZED,
            assertThrows(ResponseStatusException.class, () -> service.list(0, 20)).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
            () -> service.markRead(3L, new NotificationReadRequest(true))).getStatusCode());
        verifyNoInteractions(notifications);
    }

    private Notification notification(Long id, Instant readAt) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setRecipientKeycloakId("owner-a");
        notification.setEventType("NEW_MATCH");
        notification.setTitle("New matching properties");
        notification.setMessage("1 property now matches your saved search.");
        notification.setAlertRuleId(7L);
        notification.setSavedSearchId(4L);
        notification.setPayload("{\"propertyIds\":[90],\"newMatchCount\":1}");
        notification.setOccurredAt(now.minusSeconds(60));
        notification.setCreatedAt(now);
        notification.setReadAt(readAt);
        return notification;
    }
}
