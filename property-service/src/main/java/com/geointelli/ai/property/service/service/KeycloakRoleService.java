package com.geointelli.ai.property.service.service;

import java.util.Set;
import com.geointelli.ai.property.service.config.ApplicationRoles;

/**
 * Grants and revokes the Keycloak realm roles that GeoZentra manages (SCRUM-204). Both operations are idempotent:
 * granting a role the user already has, or revoking one they do not have, succeeds without change. The user keeps
 * the old roles until their access token is refreshed.
 */
public interface KeycloakRoleService {

    /** The only realm roles GeoZentra may grant or revoke; admin is never managed by the application. */
    Set<String> MANAGED_ROLES = Set.of(ApplicationRoles.INVESTOR, ApplicationRoles.REALTOR, ApplicationRoles.LENDER);

    /**
     * Adds a realm role to a Keycloak user.
     *
     * @param keycloakUserId Keycloak user id (the JWT subject)
     * @param role           one of {@link #MANAGED_ROLES}
     * @throws IllegalArgumentException if the role is not managed by GeoZentra
     * @throws com.geointelli.ai.property.service.exception.KeycloakRoleException 503 when role management is off or
     *         Keycloak cannot be reached, 409 when the user no longer exists in Keycloak
     */
    void grantRealmRole(String keycloakUserId, String role);

    /**
     * Removes a realm role from a Keycloak user.
     *
     * @param keycloakUserId Keycloak user id (the JWT subject)
     * @param role           one of {@link #MANAGED_ROLES}
     * @throws IllegalArgumentException if the role is not managed by GeoZentra
     * @throws com.geointelli.ai.property.service.exception.KeycloakRoleException same cases as granting
     */
    void revokeRealmRole(String keycloakUserId, String role);
}
