package com.geointelli.ai.notification.service.dto;

import java.util.Set;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.geointelli.ai.notification.service.entity.EmailCadence;

/**
 * Full replacement of delivery preferences. Recipient email comes only from a
 * verified JWT.
 */
public record NotificationPreferencesRequest(@NotNull Boolean inAppEnabled, @NotNull Boolean emailEnabled,
                @NotNull @Size(max = 2) Set<@NotNull @Pattern(regexp = "NEW_MATCH|CONTACT_REQUEST") String> enabledEventTypes,
                @NotNull EmailCadence emailCadence) {
}
