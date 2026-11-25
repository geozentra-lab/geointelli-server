package com.geointelli.ai.property.service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.config.AuthoritiesConverter;
import com.geointelli.ai.property.service.config.SecurityConfig;
import com.geointelli.ai.property.service.dto.EconomicIndicatorImportResultDTO;
import com.geointelli.ai.property.service.dto.EconomicIndicatorObservationsDTO;
import com.geointelli.ai.property.service.service.EconomicIndicatorImportService;
import com.geointelli.ai.property.service.service.EconomicIndicatorService;

/**
 * Runs the real security filter chain (SecurityConfig + AuthoritiesConverter) and the GlobalExceptionHandler in front
 * of the economic indicator endpoints (SCRUM-133): the imports need the admin realm role; reading is public. The
 * JwtDecoder is mocked: each test token decodes to a JWT with chosen realm roles.
 */
@WebMvcTest({AdminEconomicIndicatorController.class, EconomicIndicatorController.class})
@Import({SecurityConfig.class, AuthoritiesConverter.class})
class EconomicIndicatorSecurityTest {
    private static final String BLS_URL = "/api/admin/economic-indicators/import/bls-laus";
    private static final String CENSUS_URL = "/api/admin/economic-indicators/import/census-population";
    private static final String READ_URL = "/api/economic-indicators";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    EconomicIndicatorImportService importService;

    @MockBean
    EconomicIndicatorService economicIndicatorService;

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

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("admin-token")).thenReturn(jwt("admin-token", List.of("default-roles-geozentra", "admin")));
        when(jwtDecoder.decode("user-token")).thenReturn(jwt("user-token", List.of("default-roles-geozentra")));
        when(importService.importCensusPopulation(any(), any(), any())).thenReturn(new EconomicIndicatorImportResultDTO(
                "CENSUS_PEP", "12", 67, "https://www2.census.gov/", "2025", null, null, 1407, 268, 1070, 69, 0, 0,
                List.of(), Instant.parse("2026-11-25T09:00:00Z")));
        when(economicIndicatorService.getIndicators()).thenReturn(List.of());
        when(economicIndicatorService.getObservations(anyString(), anyString(), any(), any())).thenReturn(
                new EconomicIndicatorObservationsDTO("POPULATION", "PERSONS", "ANNUAL", "12086", null, null,
                        EconomicIndicatorObservationsDTO.STATUS_EMPTY, List.of()));
    }

    @Test
    @DisplayName("When importing without a token, then 401; without the admin role, then 403; nothing is imported")
    void whenImportNotAdmin_thenRefused() throws Exception {
        mockMvc.perform(post(BLS_URL).param("state", "FL")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(CENSUS_URL).param("state", "FL")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BLS_URL).param("state", "FL").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(CENSUS_URL).param("state", "FL").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("When an admin imports, then 200 with the counts, and the caller is recorded as loadedBy")
    void whenImportAdmin_thenOk() throws Exception {
        mockMvc.perform(post(CENSUS_URL).param("state", "FL").param("vintage", "2025")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(1407))
                .andExpect(jsonPath("$.revised").value(1070));
        verify(importService).importCensusPopulation("FL", 2025, "user-admin-token");

        mockMvc.perform(post(BLS_URL).param("state", "FL").param("fips", "12086")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        verify(importService).importBlsLaus("FL", "12086", null, null, "user-admin-token");
    }

    @Test
    @DisplayName("When an import is invalid, then the service errors become 400, 409 and 502")
    void whenImportFails_thenErrorStatus() throws Exception {
        when(importService.importCensusPopulation("ZZ", null, "user-admin-token"))
                .thenThrow(new IllegalArgumentException("Unknown state, or none of its counties has a FIPS code: ZZ"));
        when(importService.importCensusPopulation("FL", 2021, "user-admin-token"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "apply migration V50 first"));
        when(importService.importCensusPopulation("FL", 2022, "user-admin-token"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Census file unavailable"));

        mockMvc.perform(post(CENSUS_URL).param("state", "ZZ").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(CENSUS_URL).param("state", "FL").param("vintage", "2021")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(CENSUS_URL).param("state", "FL").param("vintage", "2022")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("When a parameter is missing or not a number, then 400 before the service is called")
    void whenParameterInvalid_thenBadRequest() throws Exception {
        mockMvc.perform(post(CENSUS_URL).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(CENSUS_URL).param("state", "FL").param("vintage", "abc")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(READ_URL + "/POPULATION/observations")).andExpect(status().isBadRequest());
        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("When reading without a token, then 200: the indicators are public reference data")
    void whenReadWithoutToken_thenOk() throws Exception {
        mockMvc.perform(get(READ_URL)).andExpect(status().isOk());
        mockMvc.perform(get(READ_URL + "/POPULATION/observations").param("fips", "12086"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMPTY"));
        verify(economicIndicatorService).getObservations("POPULATION", "12086", null, null);
    }

    @Test
    @DisplayName("When reading an unknown indicator, then 404")
    void whenReadUnknown_thenNotFound() throws Exception {
        when(economicIndicatorService.getObservations("GDP", "12086", null, null))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown economic indicator: GDP"));

        mockMvc.perform(get(READ_URL + "/GDP/observations").param("fips", "12086"))
                .andExpect(status().isNotFound());
    }
}
