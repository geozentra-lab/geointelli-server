package com.geointelli.ai.property.service.entity;

/** Why a realm role was granted or revoked (SCRUM-204, V50); {@code source_id} points to the deciding record. */
public enum RoleGrantSource {
    /** Admin approval of a role request (source_id: role_requests.id). */
    ROLE_REQUEST,
    /** Granted at once on request: the role is in onboarding.self-service-roles (source_id: role_requests.id). */
    SELF_SERVICE,
    /** Admin approval of a realtor license (source_id: realtor_license_verifications.id). */
    REALTOR_LICENSE,
    /** Revoked by an admin (source_id: the revoked role request, when there is one). */
    ADMIN_REVOCATION,
    /** Realtor role revoked because the user no longer has a verified, unexpired license. */
    LICENSE_EXPIRY
}
