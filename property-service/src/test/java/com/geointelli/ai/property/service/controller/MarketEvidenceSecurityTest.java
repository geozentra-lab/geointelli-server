package com.geointelli.ai.property.service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import com.geointelli.ai.property.service.config.AuthoritiesConverter;
import com.geointelli.ai.property.service.config.SecurityConfig;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.entity.MarketGeographyLevel;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionLaunchService;
import com.geointelli.ai.property.service.service.MarketEvidenceQueryService;

/**
 * Runs the real security filter chain (SecurityConfig + AuthoritiesConverter) in front of the market evidence
 * endpoints (SCRUM-131): reading needs any valid token, importing needs the admin realm role. The JwtDecoder is
 * mocked: each test token decodes to a JWT with chosen realm roles.
 */
@WebMvcTest(MarketEvidenceController.class)
@Import({SecurityConfig.class, AuthoritiesConverter.class})
class MarketEvidenceSecurityTest {
    private static final String READ_URL = "/api/market-evidence";
    private static final String IMPORT_URL = "/api/admin/market-evidence/import";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    IngestionLaunchService launchService;

    @MockBean
    MarketEvidenceQueryService queryService;

    @MockBean
    JwtDecoder jwtDecoder;

    private static Jwt jwt(String token, List<String> roles) {
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("user-" + token)
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "market_evidence.csv", "text/csv", "source\n".getBytes());
    }

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("admin-token")).thenReturn(jwt("admin-token", List.of("default-roles-geozentra", "admin")));
        when(jwtDecoder.decode("user-token")).thenReturn(jwt("user-token", List.of("default-roles-geozentra")));
        when(jwtDecoder.decode("bad-token")).thenThrow(new BadJwtException("invalid signature"));
        when(queryService.find(any(), anyString(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0, true, true));
        ImportJobDTO queued = new ImportJobDTO();
        queued.setId("8a9f1b2c-3d4e-4f50-9a6b-7c8d9e0f1a2b");
        when(launchService.upload(any(), anyString())).thenReturn(mock(IngestionLaunchService.InputSource.class));
        when(launchService.startCsv(any(), any(), any(), any(), any())).thenReturn(queued);
    }

    @Test
    @DisplayName("When reading without a token or with an invalid one, then 401 without calling the service")
    void whenReadWithoutValidToken_thenReturn401() throws Exception {
        mockMvc.perform(get(READ_URL).param("level", "ZIP").param("code", "33139"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(READ_URL).param("level", "ZIP").param("code", "33139")
                .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("When reading with any valid token, also without the admin role, then 200")
    void whenReadWithToken_thenReturn200() throws Exception {
        mockMvc.perform(get(READ_URL).param("level", "ZIP").param("code", "33139")
                .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk());
        verify(queryService).find(MarketGeographyLevel.ZIP, "33139", null, null, null, null, 0, 50);
    }

    @Test
    @DisplayName("When importing without a token, then 401; without the admin role, then 403; neither starts a job")
    void whenImportNotAdmin_thenRefused() throws Exception {
        mockMvc.perform(multipart(IMPORT_URL).file(file())).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(IMPORT_URL).file(file()).header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(launchService);
    }

    @Test
    @DisplayName("When an admin imports, then 202 and the job is started for the caller")
    void whenImportAdmin_thenReturn202() throws Exception {
        mockMvc.perform(multipart(IMPORT_URL).file(file()).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isAccepted());
        verify(launchService).startCsv(eq(IngestionDataset.MARKET_EVIDENCE), eq(null), eq(null), any(),
                eq("user-admin-token"));
    }
}
