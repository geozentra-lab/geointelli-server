package com.geointelli.ai.property.service.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One listing provider (SCRUM-118): who it is, how we read it, which markets it covers, how it is refreshed and
 * whether the product owner has authorized it. The row is the machine-readable copy of
 * {@code docs/listing-source-field-mapping.md}; its field mapping is in {@link ListingSourceField}. No listing data
 * lives here: the listings table (SCRUM-119) references this row by {@link #getCode() code}.
 */
@Entity
@Table(name = "listing_sources")
@Getter
@Setter
public class ListingSource extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Provider code, upper-case and unique, for example {@code REALTOR_COM}. */
    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 160)
    private String provider;

    /** How the data is read (library, API, file) and the access constraints. */
    @Column(name = "access_method", nullable = false, columnDefinition = "text")
    private String accessMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_status", nullable = false, length = 16)
    private ListingAuthorizationStatus authorizationStatus = ListingAuthorizationStatus.PENDING;

    /** Date of the product owner's decision; null while pending. */
    @Column(name = "authorized_at")
    private LocalDate authorizedAt;

    @Column(name = "licence_note", columnDefinition = "text")
    private String licenceNote;

    /** Markets covered and how they were collected. */
    @Column(nullable = false, columnDefinition = "text")
    private String coverage;

    @Enumerated(EnumType.STRING)
    @Column(name = "refresh_method", nullable = false, length = 16)
    private ListingRefreshMethod refreshMethod;

    @Column(name = "refresh_cadence", length = 120)
    private String refreshCadence;

    /** Provider fields that identify a listing across imports; never a file row number. */
    @Column(name = "identity_fields", nullable = false, columnDefinition = "text")
    private String identityFields;

    @Column(name = "known_limits", columnDefinition = "text")
    private String knownLimits;

    /** Field mapping in document order. Loaded lazily; deleted with the source. */
    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ListingSourceField> fields = new ArrayList<>();
}
