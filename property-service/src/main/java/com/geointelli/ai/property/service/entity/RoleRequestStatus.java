package com.geointelli.ai.property.service.entity;

/** Life cycle of a role request (SCRUM-204, V50). Every status except PENDING has a decision time. */
public enum RoleRequestStatus {
    /** Waiting for a GeoZentra admin; at most one per user and role. */
    PENDING,
    /** The realm role was granted in Keycloak. */
    APPROVED,
    /** An admin refused it; the reason is shown to the applicant, who may request again. */
    REJECTED,
    /** Withdrawn by the applicant before a decision. */
    CANCELLED,
    /** Approved earlier, then the realm role was revoked by an admin. */
    REVOKED
}
