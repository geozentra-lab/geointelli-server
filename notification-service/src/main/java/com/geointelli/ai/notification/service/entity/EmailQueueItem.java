package com.geointelli.ai.notification.service.entity;

import java.time.Instant;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** An event can contribute to at most one email batch. */
@Entity
@Table(name = "notification_email_queue")
@Getter
@Setter
public class EmailQueueItem {
    @Id
    private Long notificationId;
    private String recipientKeycloakId;
    private String status;
    private Long deliveryId;
    private Instant createdAt;
}
