package com.geointelli.ai.notification.service.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Owned in-app notification; recipient is a Keycloak subject, not a
 * cross-service database foreign key.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 128)
    private String eventId;
    @Column(nullable = false, length = 255)
    private String recipientKeycloakId;
    @Column(nullable = false, length = 40)
    private String eventType;
    @Column(nullable = false)
    private Long alertRuleId;
    private Long savedSearchId;
    private Long propertyId;
    @Column(nullable = false, length = 160)
    private String sourceChangeId;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 1000)
    private String message;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(nullable = false)
    private Instant occurredAt;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant readAt;
}
