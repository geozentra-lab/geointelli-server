package com.geointelli.ai.notification.service.service.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.notification.service.dto.NotificationDTO;
import com.geointelli.ai.notification.service.dto.NotificationPageDTO;
import com.geointelli.ai.notification.service.dto.NotificationReadRequest;
import com.geointelli.ai.notification.service.entity.Notification;
import com.geointelli.ai.notification.service.repository.NotificationRepository;
import com.geointelli.ai.notification.service.service.CurrentUserService;
import com.geointelli.ai.notification.service.service.NotificationService;
import tools.jackson.databind.json.JsonMapper;

/** Keeps all inbox queries owner-scoped and updates read time atomically across service instances. */
@Service
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notifications;
    private final CurrentUserService currentUser;
    private final Clock clock;
    private final JsonMapper json;

    public NotificationServiceImpl(NotificationRepository notifications, CurrentUserService currentUser,
            @Qualifier("notificationClock") Clock clock,
            @Qualifier("notificationEventMapper") JsonMapper json) {
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.clock = clock;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageDTO list(int page, int size) {
        String recipient = currentUser.getSubject();
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be nonnegative and size must be 1 to 100; offset is too large");
        }
        PageRequest request = PageRequest.of(page, size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Notification> results = notifications.findByRecipientKeycloakId(recipient, request);
        return new NotificationPageDTO(results.getContent().stream().map(this::toDTO).toList(),
            page, size, results.getTotalElements(), results.getTotalPages(), results.isFirst(), results.isLast());
    }

    @Override
    @Transactional
    public NotificationDTO markRead(Long id, NotificationReadRequest request) {
        String recipient = currentUser.getSubject();
        if (id == null || id < 1 || request == null || !Boolean.TRUE.equals(request.read())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive notification ID and read=true are required");
        }
        // The conditional UPDATE sets only readAt and preserves the first value under concurrent requests.
        notifications.markUnreadAsRead(id, recipient, clock.instant().truncatedTo(ChronoUnit.MICROS));
        Notification notification = notifications.findByIdAndRecipientKeycloakId(id, recipient)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        return toDTO(notification);
    }

    private NotificationDTO toDTO(Notification notification) {
        return new NotificationDTO(notification.getId(), notification.getEventType(), notification.getTitle(),
            notification.getMessage(), notification.getAlertRuleId(), notification.getSavedSearchId(),
            notification.getPropertyId(), json.readTree(notification.getPayload()), notification.getOccurredAt(),
            notification.getCreatedAt(), notification.getReadAt(), notification.getReadAt() != null);
    }
}
