package com.geointelli.ai.property.service.entity;

import java.time.Instant;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One realtor license submission of a user (table realtor_license_verifications, V37).
 *
 * <p>The submitted details are fixed at creation. A rejected submission is kept as history and the
 * user creates a new one. The autoCheck fields record what an automatic registry check found; the
 * decision fields record the final outcome, written by an admin (SCRUM-199, MANUAL) or by the automatic
 * check itself (AUTOMATIC). A row may carry both. This ticket leaves all of them null.
 */
@Entity
@Table(name = "realtor_license_verifications")
@Getter
@Setter
public class RealtorLicenseVerification extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner, taken from the bearer token, never from the request body. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "license_holder_name", nullable = false, updatable = false, length = 160)
    private String licenseHolderName;

    /** Two-letter US state code; only FL is accepted for now. */
    @Column(name = "licensing_state", nullable = false, updatable = false, length = 2)
    private String licensingState;

    @Column(name = "license_number", nullable = false, updatable = false, length = 40)
    private String licenseNumber;

    @Column(name = "license_expiration_date", updatable = false)
    private LocalDate licenseExpirationDate;

    @Column(name = "brokerage_name", updatable = false, length = 160)
    private String brokerageName;

    @Column(name = "work_email", updatable = false, length = 254)
    private String workEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RealtorLicenseVerificationStatus status = RealtorLicenseVerificationStatus.PENDING_REVIEW;

    /** What the automatic registry check found, if it ran; informative, the decision fields decide. */
    @Enumerated(EnumType.STRING)
    @Column(name = "auto_check_result", length = 20)
    private RealtorLicenseAutoCheckResult autoCheckResult;

    @Column(name = "auto_checked_at")
    private Instant autoCheckedAt;

    /** Registry or API the automatic check consulted. */
    @Column(name = "auto_check_source", length = 200)
    private String autoCheckSource;

    /** Free text from the automatic check (for example "name matches, license expired"). */
    @Column(name = "auto_check_note", length = 500)
    private String autoCheckNote;

    /** Filled only when status is REJECTED; shown to the owner. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** MANUAL (an admin decided) or AUTOMATIC (a registry API decided); null while pending. */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_method", length = 20)
    private RealtorLicenseDecisionMethod decisionMethod;

    /** Admin who decided a MANUAL review; internal, never exposed to the owner. Null for AUTOMATIC. */
    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Registry the admin consulted (for example the Florida DBPR licensee search). */
    @Column(name = "registry_source", length = 200)
    private String registrySource;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;
}
