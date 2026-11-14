package com.geointelli.ai.notification.service.entity;

import java.time.Instant;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Recipient-owned delivery choices, separate from property and saved-search
 * preferences.
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
public class NotificationPreference {
    @Id
    private String recipientKeycloakId;
    private boolean inAppEnabled = true;
    private boolean emailEnabled;
    private boolean newMatchEnabled = true;
    private boolean contactRequestEnabled = true;
    private boolean valuationChangedEnabled = true;
    @Enumerated(EnumType.STRING)
    private EmailCadence emailCadence = EmailCadence.DAILY;
    private String emailAddress;
    private Instant nextEmailAt;
    private Instant updatedAt;
}
