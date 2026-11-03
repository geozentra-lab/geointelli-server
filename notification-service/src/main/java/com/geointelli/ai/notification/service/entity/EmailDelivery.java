package com.geointelli.ai.notification.service.entity;

import java.time.Instant;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Durable provider attempt. ACCEPTED is not proof of final mailbox delivery.
 */
@Entity
@Table(name = "notification_email_deliveries")
@Getter
@Setter
public class EmailDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String recipientKeycloakId;
    private String recipientEmail;
    private String cadence;
    private String subject;
    private String body;
    private String status;
    private int attempts;
    private String providerMessageId;
    private String lastErrorCode;
    private Instant createdAt;
    private Instant nextAttemptAt;
    private Instant leaseExpiresAt;
    private Instant acceptedAt;
}
