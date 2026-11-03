package com.geointelli.ai.notification.service.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
/** Idempotency receipts outlive notification read/deletion state. */
@Entity
@Table(name="consumed_alert_events")
@Getter @Setter
public class ConsumedAlertEvent {
    @Id @Column(length=128) private String eventId;
    @Column(nullable=false,length=40) private String source;
    @Column(nullable=false,length=64) private String fingerprint;
    @Column(nullable=false) private Instant receivedAt;
}
