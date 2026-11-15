package com.geointelli.ai.notification.service.service.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.notification.service.config.NotificationEmailProperties;
import com.geointelli.ai.notification.service.dto.NotificationPreferencesDTO;
import com.geointelli.ai.notification.service.dto.NotificationPreferencesRequest;
import com.geointelli.ai.notification.service.entity.NotificationPreference;
import com.geointelli.ai.notification.service.repository.EmailDeliveryRepository;
import com.geointelli.ai.notification.service.repository.EmailQueueRepository;
import com.geointelli.ai.notification.service.repository.NotificationPreferenceRepository;
import com.geointelli.ai.notification.service.service.CurrentUserService;
import com.geointelli.ai.notification.service.service.EmailScheduleService;
import com.geointelli.ai.notification.service.service.NotificationPreferenceService;

/**
 * Updates choices under the same recipient lock used by ingestion and the email
 * worker.
 */
@Service
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {
    private final NotificationPreferenceRepository preferences;
    private final EmailQueueRepository queue;
    private final EmailDeliveryRepository deliveries;
    private final CurrentUserService user;
    private final EmailScheduleService schedule;
    private final NotificationEmailProperties settings;
    private final Clock clock;

    public NotificationPreferenceServiceImpl(NotificationPreferenceRepository preferences, EmailQueueRepository queue,
            EmailDeliveryRepository deliveries, CurrentUserService user, EmailScheduleService schedule,
            NotificationEmailProperties settings, @Qualifier("notificationClock") Clock clock) {
        this.preferences = preferences;
        this.queue = queue;
        this.deliveries = deliveries;
        this.user = user;
        this.schedule = schedule;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferencesDTO get() {
        return toDTO(preferences.findById(user.getSubject()).orElseGet(NotificationPreference::new));
    }

    @Override
    @Transactional
    public NotificationPreferencesDTO update(NotificationPreferencesRequest request) {
        String recipient = user.getSubject();
        if (request == null || request.inAppEnabled() == null || request.emailEnabled() == null
                || request.enabledEventTypes() == null || request.emailCadence() == null
                || request.enabledEventTypes().stream().anyMatch(type -> !("NEW_MATCH".equals(type)
                        || "CONTACT_REQUEST".equals(type) || "VALUATION_CHANGED".equals(type))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide valid channels, event types and email cadence");
        String address = request.emailEnabled() ? user.getVerifiedEmail() : null;
        NotificationPreference preference = lockForRecipient(recipient);
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        boolean routingChanged = preference.isEmailEnabled() != request.emailEnabled()
                || preference.isNewMatchEnabled() != request.enabledEventTypes().contains("NEW_MATCH")
                || preference.isContactRequestEnabled() != request.enabledEventTypes().contains("CONTACT_REQUEST")
                || preference.isValuationChangedEnabled() != request.enabledEventTypes().contains("VALUATION_CHANGED")
                || preference.getEmailCadence() != request.emailCadence()
                || !Objects.equals(address, preference.getEmailAddress());
        preference.setInAppEnabled(request.inAppEnabled());
        preference.setEmailEnabled(request.emailEnabled());
        preference.setNewMatchEnabled(request.enabledEventTypes().contains("NEW_MATCH"));
        preference.setContactRequestEnabled(request.enabledEventTypes().contains("CONTACT_REQUEST"));
        preference.setValuationChangedEnabled(request.enabledEventTypes().contains("VALUATION_CHANGED"));
        preference.setEmailAddress(address);
        preference.setEmailCadence(request.emailCadence());
        preference.setUpdatedAt(now);
        if (routingChanged) {
            preference.setNextEmailAt(request.emailEnabled() ? schedule.next(request.emailCadence(), now) : null);
            deliveries.suppressRetries(recipient);
        }
        if (!preference.isEmailEnabled()) {
            queue.suppressPending(recipient);
        } else {
            if (!preference.isNewMatchEnabled())
                queue.suppressPendingType(recipient, "NEW_MATCH");
            if (!preference.isContactRequestEnabled())
                queue.suppressPendingType(recipient, "CONTACT_REQUEST");
            if (!preference.isValuationChangedEnabled())
                queue.suppressPendingType(recipient, "VALUATION_CHANGED");
        }
        return toDTO(preference);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public NotificationPreference lockForRecipient(String recipient) {
        preferences.ensureDefault(recipient, clock.instant().truncatedTo(ChronoUnit.MICROS));
        return preferences.findForUpdate(recipient).orElseThrow();
    }

    private NotificationPreferencesDTO toDTO(NotificationPreference preference) {
        Set<String> types = new HashSet<>();
        if (preference.isNewMatchEnabled())
            types.add("NEW_MATCH");
        if (preference.isContactRequestEnabled())
            types.add("CONTACT_REQUEST");
        if (preference.isValuationChangedEnabled())
            types.add("VALUATION_CHANGED");
        return new NotificationPreferencesDTO(preference.isInAppEnabled(), preference.isEmailEnabled(),
                Set.copyOf(types), preference.getEmailCadence(),
                preference.getEmailAddress(), preference.getNextEmailAt(), preference.getUpdatedAt(),
                settings.isEnabled());
    }
}
