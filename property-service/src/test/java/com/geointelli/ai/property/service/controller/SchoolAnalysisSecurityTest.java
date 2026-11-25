package com.geointelli.ai.property.service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import com.geointelli.ai.property.service.config.AuthoritiesConverter;
import com.geointelli.ai.property.service.config.SecurityConfig;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionLaunchService;
import com.geointelli.ai.property.service.service.SchoolAnalysisService;

/**
 * The school assignment and school score v2 jobs (SCRUM-203) need the admin realm role, behind the real security
 * filter chain. Since SCRUM-94 calculate-all is an admin route for every version, v1 included.
 */
@WebMvcTest(SchoolAnalysisController.class)
@Import({SecurityConfig.class, AuthoritiesConverter.class})
class SchoolAnalysisSecurityTest {

    private static final String ASSIGN = "/api/property-school-analysis/assign";
    private static final String CALCULATE_ALL = "/api/property-school-analysis/calculate-all";
    private static final String JOB_ID = "8a9f1b2c-3d4e-4f50-9a6b-7c8d9e0f1a2b";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SchoolAnalysisService schoolAnalysisService;

    @MockBean
    IngestionLaunchService launchService;

    @MockBean
    JwtDecoder jwtDecoder;

    private static Jwt jwt(String token, List<String> roles) {
        return Jwt.withTokenValue(token).header("alg", "RS256").subject("user-" + token)
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("admin-token")).thenReturn(jwt("admin-token", List.of("default-roles-geozentra", "admin")));
        when(jwtDecoder.decode("user-token")).thenReturn(jwt("user-token", List.of("default-roles-geozentra")));
        ImportJobDTO queued = new ImportJobDTO();
        queued.setId(JOB_ID);
        when(launchService.startSchoolScoring(any(), any(), any())).thenReturn(queued);
    }

    @Test
    @DisplayName("When assign is called without a token or without the admin role, then 401 or 403 and no job")
    void assignNeedsAdmin() throws Exception {
        mockMvc.perform(post(ASSIGN).param("fips5", "12086")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ASSIGN).param("fips5", "12086").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(launchService);
    }

    @Test
    @DisplayName("When an admin calls assign, then 202 with the job and its Location")
    void adminAssigns() throws Exception {
        mockMvc.perform(post(ASSIGN).param("fips5", "12086").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/ingestion/jobs/" + JOB_ID))
                .andExpect(jsonPath("$.id").value(JOB_ID));
        verify(launchService).startSchoolScoring(IngestionDataset.SCHOOL_ASSIGNMENT, "12086", "user-admin-token");
    }

    @Test
    @DisplayName("When calculate-all asks for version 2 in any spelling, then the admin role is required")
    void scoreV2NeedsAdmin() throws Exception {
        for (String version : List.of("2", "02", " 2", "abc")) {
            mockMvc.perform(post(CALCULATE_ALL).param("version", version)).andExpect(status().isUnauthorized());
            mockMvc.perform(post(CALCULATE_ALL).param("version", version).header("Authorization", "Bearer user-token"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(CALCULATE_ALL).param("version", "1").param("version", "2"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(launchService);
        verify(schoolAnalysisService, never()).calculateAndSaveAllScores(anyBoolean());

        mockMvc.perform(post(CALCULATE_ALL).param("version", "2").param("fips5", "12086")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isAccepted());
        verify(launchService).startSchoolScoring(IngestionDataset.SCHOOL_SCORE_V2, "12086", "user-admin-token");
    }

    @Test
    @DisplayName("When calculate-all v1 is called without the admin role, then 401 or 403; an admin runs it as before")
    void scoreV1NeedsAdmin() throws Exception {
        mockMvc.perform(post(CALCULATE_ALL)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(CALCULATE_ALL).param("version", "1").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        verify(schoolAnalysisService, never()).calculateAndSaveAllScores(anyBoolean());

        mockMvc.perform(post(CALCULATE_ALL).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(CALCULATE_ALL).param("version", "1").param("recalculateExisting", "true")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());
        verify(schoolAnalysisService).calculateAndSaveAllScores(false);
        verify(schoolAnalysisService).calculateAndSaveAllScores(true);
        verifyNoInteractions(launchService);
    }

    @Test
    @DisplayName("When version 2 gets recalculateExisting, or version 1 gets fips5, or the version is 3, then 400")
    void invalidCombinations() throws Exception {
        mockMvc.perform(post(CALCULATE_ALL).param("version", "2").param("recalculateExisting", "true")
                .header("Authorization", "Bearer admin-token")).andExpect(status().isBadRequest());
        mockMvc.perform(post(CALCULATE_ALL).param("fips5", "12086").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(CALCULATE_ALL).param("version", "3").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest());
        verify(launchService, never()).startSchoolScoring(eq(IngestionDataset.SCHOOL_SCORE_V2), any(), any());
        verify(schoolAnalysisService, never()).calculateAndSaveAllScores(anyBoolean());
    }
}
