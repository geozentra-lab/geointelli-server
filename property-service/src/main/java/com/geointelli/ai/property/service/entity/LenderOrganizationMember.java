package com.geointelli.ai.property.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Explicit access grant to one organization; a user may belong to several. */
@Entity
@Table(name = "lender_organization_members", uniqueConstraints =
    @UniqueConstraint(name = "uq_lender_membership", columnNames = {"organization_id", "user_id"}))
@Getter
@Setter
public class LenderOrganizationMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false) private Long organizationId;
    @Column(nullable = false, updatable = false) private Long userId;
}

