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

/**
 * One line of a listing provider's field mapping (SCRUM-118): a provider field, the column of the listings table
 * it goes to, its type, how reliably it is filled, and a note. Rows marked {@code UNAVAILABLE} or {@code EXCLUDED}
 * have no target column; the database check constraint enforces the rule.
 */
@Entity
@Table(name = "listing_source_fields", uniqueConstraints = @UniqueConstraint(
    name = "uq_listing_source_fields", columnNames = {"source_id", "source_field"}))
@Getter
@Setter
public class ListingSourceField extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private ListingSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_group", nullable = false, length = 16)
    private ListingFieldGroup fieldGroup;

    /** Provider field; a dotted path for nested values, for example {@code location.address.city}. */
    @Column(name = "source_field", nullable = false, length = 160)
    private String sourceField;

    /** Column of the listings table; null when the field is not imported. */
    @Column(name = "target_column", length = 64)
    private String targetColumn;

    @Column(name = "data_type", length = 40)
    private String dataType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ListingFieldAvailability availability;

    @Column(columnDefinition = "text")
    private String note;

    /** Position in the document, used to return the mapping in reading order. */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
