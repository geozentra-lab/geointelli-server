package com.geointelli.ai.property.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** One user-owned event rule for exactly one property or saved search. */
@Entity
@Table(name = "alert_rules", uniqueConstraints = {
    @UniqueConstraint(name = "uq_alert_rules_user_property_event", columnNames = {"user_id", "property_id", "event_type"}),
    @UniqueConstraint(name = "uq_alert_rules_user_search_event", columnNames = {"user_id", "saved_search_id", "event_type"})})
@Getter
@Setter
public class AlertRule extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AlertEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_search_id")
    private SavedSearch savedSearch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertCadence cadence = AlertCadence.DAILY;

    @Column(nullable = false)
    private boolean paused;
}
