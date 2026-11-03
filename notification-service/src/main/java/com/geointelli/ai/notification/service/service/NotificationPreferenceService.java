package com.geointelli.ai.notification.service.service;

import com.geointelli.ai.notification.service.dto.NotificationPreferencesDTO;
import com.geointelli.ai.notification.service.dto.NotificationPreferencesRequest;
import com.geointelli.ai.notification.service.entity.NotificationPreference;

/** Delivery preferences owned by notification-service. */
public interface NotificationPreferenceService {
    NotificationPreferencesDTO get();

    NotificationPreferencesDTO update(NotificationPreferencesRequest request);

    /**
     * Internal consumption operation; the caller must already hold a transaction.
     */
    NotificationPreference lockForRecipient(String recipient);
}
