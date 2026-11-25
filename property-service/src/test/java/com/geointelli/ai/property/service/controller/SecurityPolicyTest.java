package com.geointelli.ai.property.service.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.config.AuthoritiesConverter;
import com.geointelli.ai.property.service.config.CorsConfig;
import com.geointelli.ai.property.service.config.SecurityConfig;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

/**
 * Real filter chain and JWT conversion; the probe isolates route policy from
 * business validation.
 */
@WebMvcTest(SecurityPolicyTest.Probe.class)
@Import({ SecurityConfig.class, AuthoritiesConverter.class, CorsConfig.class, SecurityPolicyTest.Probe.class })
class SecurityPolicyTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private JwtDecoder decoder;

    @BeforeEach
    void tokens() {
        for (String role : List.of("user", "investor", "realtor", "lender", "admin")) {
            when(decoder.decode(role)).thenReturn(token(role, Map.of("realm_access", Map.of("roles", List.of(role)))));
        }
        when(decoder.decode("persona-only")).thenReturn(token("persona-only", Map.of("persona", "REALTOR",
                "resource_access", Map.of("client", Map.of("roles", List.of("admin", "realtor"))))));
        when(decoder.decode("malformed"))
                .thenReturn(token("malformed", Map.of("realm_access", Map.of("roles", "admin"))));
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid token"));
        when(decoder.decode("no-subject")).thenReturn(Jwt.withTokenValue("no-subject")
                .header("alg", "RS256").claim("realm_access", Map.of("roles", List.of("admin"))).build());
    }

    private Jwt token(String value, Map<String, Object> claims) {
        return Jwt.withTokenValue(value).header("alg", "RS256").subject("subject-" + value)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claims(existing -> existing.putAll(claims)).build();
    }

    @ParameterizedTest
    @ValueSource(strings = { "user", "investor", "realtor", "lender", "admin", "persona-only" })
    void everyAuthenticatedAccountKeepsBaselineFeatures(String token) throws Exception {
        mvc.perform(get("/api/properties/search").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/entitlements").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/calculations/mortgage").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/properties/1/favorite").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/properties/1/favorite").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // property reads added after SCRUM-94 branched: forecast, price divergence, risk section, market evidence
        for (String path : List.of("/api/properties/id/1/forecast", "/api/properties/id/1/price-divergence",
                "/api/properties/id/1/location-intelligence/risk", "/api/market-evidence")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @CsvSource({ "GET,/api/clients,realtor", "POST,/api/clients,realtor", "GET,/api/tasks/7/activities,realtor",
            "GET,/api/dashboards/realtor/clients-leads,realtor", "GET,/api/lender/applications,lender",
            "POST,/api/lender/organizations,lender", "GET,/api/dashboards/lender/mortgage-context,lender",
            "GET,/api/portfolios,investor", "PATCH,/api/scenarios/7,investor", "GET,/api/opportunities,investor",
            "GET,/api/properties/id/7/opportunity-score,investor", "POST,/api/calculations/investment,investor",
            "GET,/api/dashboards/investor,investor", "GET,/api/dashboards/investor/opportunities,investor",
            "GET,/api/dashboards/investor/income-expenses-returns,investor", "POST,/api/scenarios/7/calculate,investor",
            "GET,/api/dashboards/lender/application-pipeline,lender" })
    void professionalFeatureRequiresItsOwnRoleOrAdmin(String method, String path, String role) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized());
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + role))
                .andExpect(status().isOk());
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        String other = role.equals("realtor") ? "lender" : "realtor";
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({ "POST,/api/ingestion/run", "GET,/api/ingestion/jobs/1", "POST,/api/images/import/csv",
            "POST,/api/properties/images/backfill", "GET,/api/properties/images/backfill/jobs/1",
            "POST,/api/predictions/bulk", "POST,/api/property-school-analysis/calculate-all",
            "POST,/api/property-school-analysis/7/calculate-property-score",
            "POST,/api/property-transportation-analysis/calculate-all",
            "POST,/api/property-transportation-analysis/7/calculate-property-score",
            "GET,/api/admin/valuation-refresh-requests", "POST,/api/admin/opportunity-scores/refresh",
            "POST,/api/property-school-analysis/assign", "GET,/api/admin/coverage-health",
            "PATCH,/api/admin/data-quality/issues/DQ-1", "POST,/api/admin/market-evidence/import",
            "GET,/api/admin/realtor-license-verifications", "POST,/api/admin/realtor-license-verifications/7/approve" })
    void operationsRequireAdminIncludingLegacyRoutes(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized());
        for (String role : List.of("user", "investor", "realtor", "lender")) {
            mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + role))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
    }

    @Test
    void personaClientRolesAndMalformedClaimsCannotGrantPrivileges() throws Exception {
        for (String token : List.of("persona-only", "malformed")) {
            mvc.perform(get("/api/admin/import-jobs").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/clients").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void readOnlyBulkLookupsRemainBaselineAndUnknownWritesAreDenied() throws Exception {
        mvc.perform(post("/api/property-school-analysis/bulk").header("Authorization", "Bearer user"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/property-transportation-analysis/bulk").header("Authorization", "Bearer user"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/properties/id/7").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/new-unclassified-feature").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    void baselineRejectsMissingAndInvalidBearerTokens() throws Exception {
        mvc.perform(get("/api/properties/search")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/properties/search").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/properties/id/1/location-intelligence/risk")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/market-evidence")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/import-jobs").header("Authorization", "Bearer no-subject"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserPreflightUsesConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/users/me").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(options("/api/users/me").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "PATCH")).andExpect(status().isForbidden());
    }

    @Test
    void documentationAndHealthRemainPublicButOtherActuatorRoutesDoNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env").header("Authorization", "Bearer admin")).andExpect(status().isForbidden());
    }

    @Test
    void internalErrorsAreNotReplacedByAuthorizationErrors() throws Exception {
        mvc.perform(get("/error").with(request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            return request;
        })
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 422)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/error").header("Authorization", "Bearer admin")).andExpect(status().isForbidden());
    }

    @RestController
    static class Probe {
        @RequestMapping("/**")
        public ResponseEntity<Void> respond() {
            return ResponseEntity.ok().build();
        }
    }
}
