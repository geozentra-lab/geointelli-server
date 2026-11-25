package com.geointelli.ai.property.service.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Keycloak Admin REST API access used to grant and revoke realm roles after an approval (SCRUM-204).
 *
 * <p>Uses a dedicated confidential client with a service account (client credentials), never a person's login.
 * The service account needs only the realm-management roles view-users, manage-users and view-realm. While
 * {@code enabled} is false every grant or revocation is refused with 503 and nothing changes.
 * {@code serverUrl} and {@code realm} default to the parts of the JWT issuer URI
 * ({@code http://host:port/realms/geozentra}).
 */
@Component
@ConfigurationProperties(prefix = "keycloak.role-admin")
@Validated
@Getter
@Setter
public class KeycloakRoleAdminProperties {
    private boolean enabled;
    /** Keycloak base URL, for example http://localhost:8090; empty: taken from the issuer URI. */
    private String serverUrl;
    /** Realm of the GeoZentra users; empty: taken from the issuer URI. */
    private String realm;
    private String clientId;
    private String clientSecret;
    @Min(1) @Max(60) private int timeoutSeconds = 5;
}
