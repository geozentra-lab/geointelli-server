package com.geointelli.ai.property.service.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Frozen event bytes and stable identity survive crashes, retries and changes to CRM records. */
@Entity
@Table(name = "contact_request_outbox")
@Getter
@Setter
public class ContactRequestOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, updatable = false) private Long requestId;
    @Column(nullable = false, unique = true, length = 128, updatable = false) private String eventId;
    @Column(nullable = false, columnDefinition = "text", updatable = false) private String eventJson;
    @Column(nullable = false, length = 20) private String status = "PENDING";
    private int attempts;
    @Column(nullable = false) private Instant nextAttemptAt;
    @Column(length = 80) private String lastErrorCode;
    private Instant sentAt;
}

