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
import lombok.Getter;
import lombok.Setter;

/** An enquiry linked to an owned contact; the original submission identity never changes. */
@Entity
@Table(name = "crm_leads")
@Getter
@Setter
public class CrmLead extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private CrmClient client;
    private Long propertyId;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 80)
    private String source;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStage stage = LeadStage.NEW;
    @Column(length = 2000)
    private String notes;
    @Column(nullable = false, length = 100, updatable = false)
    private String requestKey;
    @Column(nullable = false, length = 64, updatable = false)
    private String requestHash;
}
