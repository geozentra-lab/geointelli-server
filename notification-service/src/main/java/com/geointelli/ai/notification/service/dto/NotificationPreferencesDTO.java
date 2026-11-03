package com.geointelli.ai.notification.service.dto;

import java.time.Instant;
import java.util.Set;
import com.geointelli.ai.notification.service.entity.EmailCadence;

/**
 * Current-user choices and availability of the configured background sender.
 */
public record NotificationPreferencesDTO(boolean inAppEnabled, boolean emailEnabled, Set<String> enabledEventTypes,
                EmailCadence emailCadence, String emailAddress, Instant nextEmailAt, Instant updatedAt,
                boolean emailDeliveryEnabled) {
}
