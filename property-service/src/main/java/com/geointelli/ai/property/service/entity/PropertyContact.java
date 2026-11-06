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

/** Immutable submission and consent evidence. Property/lead IDs are retained historical references. */
@Entity
@Table(name = "property_contact_requests")
@Getter
@Setter
public class PropertyContact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long requesterUserId;
    @Column(nullable = false, updatable = false) private Long professionalUserId;
    @Column(nullable = false, updatable = false) private Long propertyId;
    private Long leadId;
    @Column(nullable = false, length = 20, updatable = false) private String status = "SUBMITTED";
    @Column(nullable = false, length = 120, updatable = false) private String contactName;
    @Column(length = 254, updatable = false) private String contactEmail;
    @Column(length = 40, updatable = false) private String contactPhone;
    @Column(length = 2000, updatable = false) private String message;
    @Column(nullable = false, updatable = false) private boolean consent;
    @Column(nullable = false, length = 80, updatable = false) private String consentVersion;
    @Column(nullable = false, updatable = false) private Instant submittedAt;
    @Column(nullable = false, length = 100, updatable = false) private String requestKey;
    @Column(nullable = false, length = 64, updatable = false) private String requestHash;
}

