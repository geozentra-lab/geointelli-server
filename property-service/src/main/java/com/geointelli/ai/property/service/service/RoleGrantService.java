package com.geointelli.ai.property.service.service;

import java.util.List;
import com.geointelli.ai.property.service.dto.RealtorLicenseExpiryRunDTO;
import com.geointelli.ai.property.service.dto.RoleGrantEventDTO;
import com.geointelli.ai.property.service.entity.RoleGrantEvent;
import com.geointelli.ai.property.service.entity.RoleGrantSource;
import com.geointelli.ai.property.service.entity.User;

/**
 * Changes Keycloak realm roles for GeoZentra decisions and records each change (SCRUM-204). Keycloak is called first;
 * the history row is written only when Keycloak accepted the change, so a failure leaves no trace and changes nothing.
 */
public interface RoleGrantService {

    /**
     * Grants a realm role in Keycloak, then records it.
     *
     * @param user            the user receiving the role
     * @param role            investor, realtor or lender
     * @param source          what decided it
     * @param sourceId        id of the deciding record, or null
     * @param reason          why, or null
     * @param decidedByUserId deciding admin, or null for automatic decisions
     * @return the recorded event
     * @throws com.geointelli.ai.property.service.exception.KeycloakRoleException when Keycloak refused or is unreachable
     */
    RoleGrantEvent grant(User user, String role, RoleGrantSource source, Long sourceId, String reason,
                         Long decidedByUserId);

    /**
     * Revokes a realm role in Keycloak, then records it. Same parameters and failures as {@link #grant}.
     *
     * @return the recorded event
     */
    RoleGrantEvent revoke(User user, String role, RoleGrantSource source, Long sourceId, String reason,
                          Long decidedByUserId);

    /**
     * Grant and revocation history of a user, newest first.
     *
     * @param userId the user
     * @return the events; empty when GeoZentra never changed a role of this user
     * @throws org.springframework.web.server.ResponseStatusException 404 when the user does not exist
     */
    List<RoleGrantEventDTO> history(Long userId);

    /**
     * Revokes a realm role on an admin's decision; the admin is the user behind the bearer token. The newest
     * approved role request of that user and role, if any, becomes REVOKED.
     *
     * @param userId the user losing the role
     * @param role   investor, realtor or lender
     * @param reason why; required
     * @return the recorded event
     * @throws IllegalArgumentException if the role is not managed by GeoZentra or the reason is missing
     * @throws org.springframework.web.server.ResponseStatusException 404 when the user does not exist
     */
    RoleGrantEventDTO revokeByAdmin(Long userId, String role, String reason);

    /**
     * Revokes the realtor role GeoZentra granted to users who no longer have a verified license valid today (UTC).
     * Each user is handled in its own transaction: one Keycloak failure does not stop the others.
     *
     * @return how many users were checked, revoked and failed
     */
    RealtorLicenseExpiryRunDTO revokeExpiredRealtorGrants();
}
