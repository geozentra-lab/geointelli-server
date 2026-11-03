package com.geointelli.ai.notification.service.service.impl;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.notification.service.dto.AlertEventEnvelopeDTO;
import com.geointelli.ai.notification.service.dto.DecodedAlertEventDTO;
import com.geointelli.ai.notification.service.entity.ConsumedAlertEvent;
import com.geointelli.ai.notification.service.entity.Notification;
import com.geointelli.ai.notification.service.exception.InvalidAlertEventException;
import com.geointelli.ai.notification.service.repository.ConsumedAlertEventRepository;
import com.geointelli.ai.notification.service.repository.NotificationRepository;
import com.geointelli.ai.notification.service.service.AlertEventConsumptionService;
import com.geointelli.ai.notification.service.service.NotificationPreferenceService;
import com.geointelli.ai.notification.service.repository.EmailQueueRepository;

/**
 * Receipt and notification commit together before listener success allows the
 * RabbitMQ acknowledgement.
 */
@Service
public class AlertEventConsumptionServiceImpl implements AlertEventConsumptionService {
    private final ConsumedAlertEventRepository receipts;
    private final NotificationRepository notifications;
    private final Clock clock;
    private final NotificationPreferenceService preferences;
    private final EmailQueueRepository emailQueue;

    public AlertEventConsumptionServiceImpl(ConsumedAlertEventRepository receipts, NotificationRepository notifications,
            @Qualifier("notificationClock") Clock clock, NotificationPreferenceService preferences,
            EmailQueueRepository emailQueue) {
        this.receipts = receipts;
        this.notifications = notifications;
        this.clock = clock;
        this.preferences = preferences;
        this.emailQueue = emailQueue;
    }

    @Override
    @Transactional
    public boolean consume(DecodedAlertEventDTO decoded) {
        AlertEventEnvelopeDTO event = decoded.envelope();
        Instant receivedAt = clock.instant();
        int claimed = receipts.claim(event.eventId(), event.source(), decoded.fingerprint(), receivedAt);
        if (claimed == 0) {
            ConsumedAlertEvent existing = receipts.findById(event.eventId()).orElseThrow();
            if (!decoded.fingerprint().equals(existing.getFingerprint()))
                throw new InvalidAlertEventException("EVENT_ID_COLLISION");
            return false;
        }
        var preference = preferences.lockForRecipient(event.userId());
        if (!preference.isNewMatchEnabled() || (!preference.isInAppEnabled() && !preference.isEmailEnabled()))
            return true;
        Notification notification = new Notification();
        notification.setInAppVisible(preference.isInAppEnabled());
        notification.setEventId(event.eventId());
        notification.setRecipientKeycloakId(event.userId());
        notification.setEventType(event.eventType());
        notification.setAlertRuleId(event.alertRuleId());
        notification.setSavedSearchId(event.savedSearchId());
        notification.setPropertyId(event.propertyId());
        notification.setSourceChangeId(event.sourceChangeId());
        notification.setOccurredAt(event.occurredAt());
        notification.setCreatedAt(receivedAt);
        notification.setPayload(event.payload().toString());
        long count = decoded.payload().newMatchCount();
        notification.setTitle("New matching properties");
        notification.setMessage(count == 1 ? "1 property now matches your saved search."
                : count + " properties now match your saved search.");
        notifications.saveAndFlush(notification);
        if (preference.isEmailEnabled())
            emailQueue.enqueue(notification.getId(), event.userId(), receivedAt);
        return true;
    }
}
