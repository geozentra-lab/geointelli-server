package com.geointelli.ai.property.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A realm role could not be granted or revoked in Keycloak (SCRUM-204). Thrown before anything is written, so the
 * decision that needed it is not recorded: a role request stays PENDING, a license stays PENDING_REVIEW. The
 * message is safe to show; the technical cause is only logged.
 */
public class KeycloakRoleException extends ResponseStatusException {

    public KeycloakRoleException(HttpStatus status, String reason) {
        super(status, reason);
    }

    /** Keycloak role management is switched off or could not be reached. */
    public static KeycloakRoleException unavailable(String reason) {
        return new KeycloakRoleException(HttpStatus.SERVICE_UNAVAILABLE, reason);
    }
}
