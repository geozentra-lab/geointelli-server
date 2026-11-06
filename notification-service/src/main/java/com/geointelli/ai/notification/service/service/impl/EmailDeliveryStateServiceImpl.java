package com.geointelli.ai.notification.service.service.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.notification.service.config.NotificationEmailProperties;
import com.geointelli.ai.notification.service.dto.EmailDispatchDTO;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO;
import com.geointelli.ai.notification.service.entity.EmailCadence;
import com.geointelli.ai.notification.service.entity.EmailDelivery;
import com.geointelli.ai.notification.service.repository.EmailDeliveryRepository;
import com.geointelli.ai.notification.service.repository.EmailQueueRepository;
import com.geointelli.ai.notification.service.repository.NotificationPreferenceRepository;
import com.geointelli.ai.notification.service.service.EmailDeliveryStateService;
import com.geointelli.ai.notification.service.service.EmailScheduleService;

/**
 * Recipient row locks allow independent users to be dispatched by different
 * application instances.
 */
@Service
public class EmailDeliveryStateServiceImpl implements EmailDeliveryStateService {
    private final NotificationPreferenceRepository preferences;
    private final EmailDeliveryRepository deliveries;
    private final EmailQueueRepository queue;
    private final EmailScheduleService schedule;
    private final NotificationEmailProperties settings;
    private final Clock clock;

    public EmailDeliveryStateServiceImpl(NotificationPreferenceRepository preferences,
            EmailDeliveryRepository deliveries,
            EmailQueueRepository queue, EmailScheduleService schedule, NotificationEmailProperties settings,
            @Qualifier("notificationClock") Clock clock) {
        this.preferences = preferences;
        this.deliveries = deliveries;
        this.queue = queue;
        this.schedule = schedule;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void recoverExpired() {
        deliveries.recoverExpired(clock.instant());
    }

    @Override
    @Transactional(timeout = 30)
    public EmailDispatchDTO prepareNext() {
        if (!settings.isEnabled())
            return null;
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var preference = preferences.lockNextDue(now).orElse(null);
        if (preference == null)
            return null;
        String recipient = preference.getRecipientKeycloakId();
        EmailDelivery delivery = deliveries
                .findFirstByRecipientKeycloakIdAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                        recipient, "RETRY", now)
                .orElse(null);
        if (delivery == null) {
            int limit = preference.getEmailCadence() == EmailCadence.IMMEDIATE ? 1 : settings.getMaxDigestItems();
            List<EmailQueueRepository.Item> items = queue.pending(recipient, limit);
            if (items.isEmpty())
                return null;
            delivery = new EmailDelivery();
            delivery.setRecipientKeycloakId(recipient);
            delivery.setRecipientEmail(preference.getEmailAddress());
            delivery.setCadence(preference.getEmailCadence().name());
            delivery.setCreatedAt(now);
            delivery.setSubject(preference.getEmailCadence() == EmailCadence.IMMEDIATE ? "New GeoZentra alert"
                    : "Your GeoZentra alert digest");
            StringBuilder body = new StringBuilder("Your GeoZentra notifications:\n\n");
            for (EmailQueueRepository.Item item : items) {
                body.append(item.ruleName() == null ? "Saved search" : item.ruleName()).append(": ")
                        .append(item.message()).append('\n');
            }
            body.append("\nOpen your GeoZentra inbox for full details.\n");
            if (preference.getEmailCadence() != EmailCadence.IMMEDIATE)
                body.append("This digest contains up to ").append(limit)
                        .append(" notification summaries. Remaining queued items continue in the next digest.\n");
            delivery.setBody(body.toString());
            delivery.setAttempts(1);
            delivery.setStatus("SENDING");
            delivery.setNextAttemptAt(now);
            delivery.setLeaseExpiresAt(now.plusSeconds(settings.getLeaseSeconds()));
            deliveries.saveAndFlush(delivery);
            queue.assign(items, delivery.getId());
            preference.setNextEmailAt(schedule.next(preference.getEmailCadence(), now));
        } else {
            if (!Objects.equals(delivery.getRecipientEmail(), preference.getEmailAddress())
                    || !delivery.getCadence().equals(preference.getEmailCadence().name())) {
                delivery.setStatus("SUPPRESSED");
                delivery.setLastErrorCode("PREFERENCES_CHANGED");
                return null;
            }
            if (delivery.getAttempts() >= settings.getMaxAttempts()) {
                delivery.setStatus("FAILED");
                delivery.setLastErrorCode("ATTEMPTS_EXHAUSTED");
                return null;
            }
            // Only explicit provider rejection may enter RETRY. Frozen batches keep their
            // original content.
            delivery.setStatus("SENDING");
            delivery.setAttempts(delivery.getAttempts() + 1);
            delivery.setLeaseExpiresAt(now.plusSeconds(settings.getLeaseSeconds()));
        }
        return new EmailDispatchDTO(delivery.getId(), delivery.getAttempts(), delivery.getRecipientEmail(),
                delivery.getSubject(), delivery.getBody());
    }

    @Override
    @Transactional
    public void finish(EmailDispatchDTO dispatch, EmailSendResultDTO result) {
        EmailDelivery delivery = deliveries.findById(dispatch.id()).orElseThrow();
        // A late, known response may resolve UNKNOWN, but can never overwrite a
        // different attempt or accepted send.
        if (delivery.getAttempts() != dispatch.attempt()
                || (!"SENDING".equals(delivery.getStatus()) && !"UNKNOWN".equals(delivery.getStatus())))
            return;
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        delivery.setLeaseExpiresAt(null);
        delivery.setLastErrorCode(result.errorCode());
        switch (result.outcome()) {
            case ACCEPTED -> {
                if (result.providerMessageId() == null || result.providerMessageId().isBlank()) {
                    delivery.setStatus("UNKNOWN");
                    delivery.setLastErrorCode("MISSING_PROVIDER_MESSAGE_ID");
                } else {
                    delivery.setStatus("ACCEPTED");
                    delivery.setProviderMessageId(result.providerMessageId());
                    delivery.setAcceptedAt(now);
                    delivery.setLastErrorCode(null);
                }
            }
            case RETRYABLE_REJECTION -> {
                delivery.setStatus(delivery.getAttempts() < settings.getMaxAttempts() ? "RETRY" : "FAILED");
                long delay = Math.min(3600L,
                        settings.getRetryDelaySeconds() * (1L << Math.min(10, delivery.getAttempts() - 1)));
                delivery.setNextAttemptAt(now.plusSeconds(delay));
            }
            case REJECTED -> delivery.setStatus("FAILED");
            case UNKNOWN -> delivery.setStatus("UNKNOWN");
        }
    }
}
