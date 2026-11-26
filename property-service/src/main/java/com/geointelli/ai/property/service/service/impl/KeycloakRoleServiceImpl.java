package com.geointelli.ai.property.service.service.impl;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import com.geointelli.ai.property.service.config.KeycloakRoleAdminProperties;
import com.geointelli.ai.property.service.exception.KeycloakRoleException;
import com.geointelli.ai.property.service.service.KeycloakRoleService;

/**
 * {@link KeycloakRoleService} on the Keycloak Admin REST API (SCRUM-204), the API the official admin client wraps,
 * called with Spring's RestClient so no Keycloak library or RESTEasy runtime is added next to Spring Boot.
 *
 * <p>Calls: a client-credentials token of the dedicated role-management client (cached until 30 s before it
 * expires), {@code GET /admin/realms/{realm}/roles/{role}} for the role representation, then
 * {@code POST} or {@code DELETE /admin/realms/{realm}/users/{id}/role-mappings/realm}. Both mapping calls are
 * idempotent in Keycloak. A rejected token is renewed once. Secrets and tokens are never logged.
 */
@Slf4j
@Service
public class KeycloakRoleServiceImpl implements KeycloakRoleService {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final long TOKEN_MARGIN_SECONDS = 30;

    private final KeycloakRoleAdminProperties properties;
    private final RestClient rest;
    private final String serverUrl;
    private final String realm;

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    @Autowired
    public KeycloakRoleServiceImpl(KeycloakRoleAdminProperties properties, RestClient.Builder builder,
                                   @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) {
        this(properties, timedClient(builder, properties.getTimeoutSeconds()), issuerUri);
    }

    /** For tests: uses the given client as is. */
    KeycloakRoleServiceImpl(KeycloakRoleAdminProperties properties, RestClient rest, String issuerUri) {
        this.properties = properties;
        this.rest = rest;
        String[] issuer = splitIssuer(issuerUri);
        this.serverUrl = trimSlash(hasText(properties.getServerUrl()) ? properties.getServerUrl() : issuer[0]);
        this.realm = hasText(properties.getRealm()) ? properties.getRealm().trim() : issuer[1];
    }

    @Override
    public void grantRealmRole(String keycloakUserId, String role) {
        changeMapping(HttpMethod.POST, keycloakUserId, role);
    }

    @Override
    public void revokeRealmRole(String keycloakUserId, String role) {
        changeMapping(HttpMethod.DELETE, keycloakUserId, role);
    }

    private void changeMapping(HttpMethod method, String keycloakUserId, String role) {
        if (role == null || !MANAGED_ROLES.contains(role)) {
            throw new IllegalArgumentException("Role " + role + " is not managed by GeoZentra");
        }
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new IllegalArgumentException("The user has no Keycloak id");
        }
        requireConfigured();
        String verb = method == HttpMethod.POST ? "grant" : "revoke";

        Map<String, Object> roleRepresentation;
        try {
            roleRepresentation = withToken(token -> rest.get()
                .uri(serverUrl + "/admin/realms/{realm}/roles/{role}", realm, role)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().body(JSON_OBJECT));
        } catch (RestClientResponseException e) {
            log.warn("Keycloak role lookup failed for role {}: HTTP {}", role, e.getStatusCode().value());
            if (e.getStatusCode().value() == 404) {
                throw KeycloakRoleException.unavailable("The realm role '" + role + "' does not exist in Keycloak; "
                    + "create it before approving. Nothing was changed");
            }
            throw refused(e);
        } catch (RestClientException e) {
            throw unreachable(verb, role, e);
        }

        try {
            withToken(token -> rest.method(method)
                .uri(serverUrl + "/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakUserId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(roleRepresentation))
                .retrieve().toBodilessEntity());
        } catch (RestClientResponseException e) {
            log.warn("Keycloak role {} failed for role {}: HTTP {}", verb, role, e.getStatusCode().value());
            if (e.getStatusCode().value() == 404) {
                throw new KeycloakRoleException(HttpStatus.CONFLICT,
                    "The user no longer exists in Keycloak; nothing was changed");
            }
            throw refused(e);
        } catch (RestClientException e) {
            throw unreachable(verb, role, e);
        }
        log.info("Keycloak realm role {} {} for user {}", role, method == HttpMethod.POST ? "granted" : "revoked",
            keycloakUserId);
    }

    /** Runs a call with the cached token; a 401 means the token was invalidated, so it is renewed and retried once. */
    private <T> T withToken(Function<String, T> call) {
        try {
            return call.apply(token());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 401) {
                throw e;
            }
            clearToken();
            return call.apply(token());
        }
    }

    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt)) {
            return accessToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        Map<String, Object> body;
        try {
            body = rest.post()
                .uri(serverUrl + "/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(JSON_OBJECT);
        } catch (RestClientResponseException e) {
            log.warn("Keycloak refused the role-management client credentials: HTTP {}", e.getStatusCode().value());
            throw KeycloakRoleException.unavailable(
                "Keycloak refused the role-management client; check its id and secret. Nothing was changed");
        }
        Object token = body == null ? null : body.get("access_token");
        if (!(token instanceof String value) || value.isBlank()) {
            throw KeycloakRoleException.unavailable("Keycloak returned no access token; nothing was changed");
        }
        long expiresIn = body.get("expires_in") instanceof Number number ? number.longValue() : 60;
        accessToken = value;
        accessTokenExpiresAt = Instant.now().plusSeconds(Math.max(0, expiresIn - TOKEN_MARGIN_SECONDS));
        return accessToken;
    }

    private synchronized void clearToken() {
        accessToken = null;
        accessTokenExpiresAt = Instant.EPOCH;
    }

    private void requireConfigured() {
        if (!properties.isEnabled()) {
            throw KeycloakRoleException.unavailable(
                "Keycloak role management is not enabled (keycloak.role-admin.enabled); nothing was changed");
        }
        if (!hasText(serverUrl) || !hasText(realm) || !hasText(properties.getClientId())
                || !hasText(properties.getClientSecret())) {
            throw KeycloakRoleException.unavailable(
                "Keycloak role management is not configured (server URL, realm, client id and secret); "
                    + "nothing was changed");
        }
    }

    private static KeycloakRoleException refused(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            return KeycloakRoleException.unavailable("Keycloak refused the role change; the role-management client "
                + "needs the realm-management roles view-users, manage-users and view-realm. Nothing was changed");
        }
        return KeycloakRoleException.unavailable("Keycloak answered HTTP " + status + "; nothing was changed");
    }

    private static KeycloakRoleException unreachable(String verb, String role, RestClientException e) {
        log.warn("Keycloak could not be reached to {} role {}: {}", verb, role, e.getClass().getSimpleName());
        return KeycloakRoleException.unavailable("Keycloak could not be reached; nothing was changed");
    }

    private static RestClient timedClient(RestClient.Builder builder, int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return builder.requestFactory(factory).build();
    }

    /** {@code http://host:port/realms/geozentra} gives {@code [http://host:port, geozentra]}. */
    static String[] splitIssuer(String issuerUri) {
        if (!hasText(issuerUri)) {
            return new String[] {null, null};
        }
        String issuer = trimSlash(issuerUri.trim());
        int index = issuer.indexOf("/realms/");
        if (index < 0) {
            return new String[] {null, null};
        }
        return new String[] {issuer.substring(0, index), issuer.substring(index + "/realms/".length())};
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
