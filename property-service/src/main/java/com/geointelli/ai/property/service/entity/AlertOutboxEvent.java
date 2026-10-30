package com.geointelli.ai.property.service.entity;

import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A committed change awaiting at-least-once delivery; its ID remains stable on every retry. */
@Entity
@Table(name = "alert_outbox")
@Getter
@Setter
public class AlertOutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ruleId;
    private long generation;
    private Long savedSearchId;
    private Long propertyId;
    private String recipientKeycloakId;
    @Enumerated(EnumType.STRING)
    private AlertEventType eventType;
    private Instant occurredAt;
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;
    private String status = "PENDING";
    private int attempts;
    private Instant nextAttemptAt;
    private String lastErrorCode;
    private Instant sentAt;
}
