package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.geointelli.ai.property.service.config.KeycloakRoleAdminProperties;
import com.geointelli.ai.property.service.exception.KeycloakRoleException;

/** Keycloak Admin REST calls of SCRUM-204 against a mocked Keycloak. */
class KeycloakRoleServiceImplTest {

    private static final String ISSUER = "http://kc.test:8090/realms/geozentra";
    private static final String TOKEN_URL = "http://kc.test:8090/realms/geozentra/protocol/openid-connect/token";
    private static final String ROLE_URL = "http://kc.test:8090/admin/realms/geozentra/roles/lender";
    private static final String MAPPING_URL = "http://kc.test:8090/admin/realms/geozentra/users/kc-123/role-mappings/realm";
    private static final String ROLE_JSON = "{\"id\":\"role-uuid\",\"name\":\"lender\",\"composite\":false}";

    private KeycloakRoleAdminProperties properties;
    private MockRestServiceServer server;
    private KeycloakRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new KeycloakRoleAdminProperties();
        properties.setEnabled(true);
        properties.setClientId("geozentra-role-admin");
        properties.setClientSecret("s3cret");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new KeycloakRoleServiceImpl(properties, builder.build(), ISSUER);
    }

    private void expectToken(String token) {
        server.expect(once(), requestTo(TOKEN_URL)).andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().formDataContains(java.util.Map.of("grant_type", "client_credentials",
                "client_id", "geozentra-role-admin", "client_secret", "s3cret")))
            .andRespond(withSuccess("{\"access_token\":\"" + token + "\",\"expires_in\":300}",
                MediaType.APPLICATION_JSON));
    }

    private void expectRole(String token) {
        server.expect(once(), requestTo(ROLE_URL)).andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + token))
            .andRespond(withSuccess(ROLE_JSON, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Grant: client-credentials token, role lookup, then POST of the role to the user's realm mappings")
    void grantPostsRoleMapping() {
        expectToken("t1");
        expectRole("t1");
        server.expect(once(), requestTo(MAPPING_URL)).andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer t1"))
            .andExpect(content().json("[" + ROLE_JSON + "]"))
            .andRespond(withNoContent());

        service.grantRealmRole("kc-123", "lender");

        server.verify();
    }

    @Test
    @DisplayName("Revoke: DELETE of the same mapping; the token is reused while valid")
    void revokeDeletesRoleMappingAndReusesToken() {
        expectToken("t1");
        expectRole("t1");
        server.expect(once(), requestTo(MAPPING_URL)).andExpect(method(HttpMethod.POST)).andRespond(withNoContent());
        expectRole("t1");
        server.expect(once(), requestTo(MAPPING_URL)).andExpect(method(HttpMethod.DELETE))
            .andExpect(content().json("[" + ROLE_JSON + "]"))
            .andRespond(withNoContent());

        service.grantRealmRole("kc-123", "lender");
        service.revokeRealmRole("kc-123", "lender");

        server.verify();
    }

    @Test
    @DisplayName("A token Keycloak no longer accepts (401) is renewed once and the call retried")
    void rejectedTokenIsRenewedOnce() {
        expectToken("old");
        server.expect(once(), requestTo(ROLE_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken("new");
        expectRole("new");
        server.expect(once(), requestTo(MAPPING_URL)).andExpect(header("Authorization", "Bearer new"))
            .andRespond(withNoContent());

        service.grantRealmRole("kc-123", "lender");

        server.verify();
    }

    @Test
    @DisplayName("Disabled role management answers 503 without calling Keycloak")
    void disabledIsUnavailable() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.grantRealmRole("kc-123", "lender"))
            .isInstanceOfSatisfying(KeycloakRoleException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(e.getReason()).contains("not enabled");
            });
        server.verify();
    }

    @Test
    @DisplayName("Missing client secret answers 503 without calling Keycloak")
    void missingSecretIsUnavailable() {
        properties.setClientSecret(" ");

        assertThatThrownBy(() -> service.grantRealmRole("kc-123", "lender"))
            .isInstanceOfSatisfying(KeycloakRoleException.class,
                e -> assertThat(e.getReason()).contains("not configured"));
        server.verify();
    }

    @Test
    @DisplayName("admin and unknown roles are never managed by the application")
    void onlyManagedRoles() {
        assertThatThrownBy(() -> service.grantRealmRole("kc-123", "admin"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.revokeRealmRole("kc-123", "superuser"))
            .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    @DisplayName("A user missing in Keycloak is 409")
    void userNotFound() {
        expectToken("t1");
        expectRole("t1");
        server.expect(once(), requestTo(MAPPING_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.grantRealmRole("kc-123", "lender"))
            .isInstanceOfSatisfying(KeycloakRoleException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getReason()).contains("no longer exists");
            });
        server.verify();
    }

    @Test
    @DisplayName("A realm role missing in Keycloak is 503 asking to create it; the mapping is not called")
    void roleNotFound() {
        expectToken("t1");
        server.expect(once(), requestTo(ROLE_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.grantRealmRole("kc-123", "lender"))
            .isInstanceOfSatisfying(KeycloakRoleException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(e.getReason()).contains("does not exist in Keycloak");
            });
        server.verify();
    }

    @Test
    @DisplayName("Missing realm-management permissions (403) are 503 naming the roles, never the secret")
    void missingPermissions() {
        expectToken("t1");
        expectRole("t1");
        server.expect(once(), requestTo(MAPPING_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.grantRealmRole("kc-123", "lender"))
            .isInstanceOfSatisfying(KeycloakRoleException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(e.getReason()).contains("manage-users").doesNotContain("s3cret");
            });
        server.verify();
    }

    @Test
    @DisplayName("Unreachable Keycloak is 503")
    void unreachable() {
        expectToken("t1");
        server.expect(once(), requestTo(ROLE_URL)).andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> service.grantRealmRole("kc-123", "lender"))
            .isInstanceOfSatisfying(KeycloakRoleException.class,
                e -> assertThat(e.getReason()).contains("could not be reached"));
        server.verify();
    }

    @Test
    @DisplayName("Wrong client credentials are 503 and nothing else is called")
    void wrongCredentials() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.grantRealmRole("kc-123", "lender"))
            .isInstanceOfSatisfying(KeycloakRoleException.class,
                e -> assertThat(e.getReason()).contains("check its id and secret").doesNotContain("s3cret"));
        server.verify();
    }

    @Test
    @DisplayName("Server URL and realm come from the issuer URI unless set explicitly")
    void issuerSplit() {
        assertThat(KeycloakRoleServiceImpl.splitIssuer("http://host:8090/realms/geozentra/"))
            .containsExactly("http://host:8090", "geozentra");
        assertThat(KeycloakRoleServiceImpl.splitIssuer("")).containsExactly(null, null);
        assertThat(KeycloakRoleServiceImpl.splitIssuer("http://host/no-realm")).containsExactly(null, null);
    }
}
