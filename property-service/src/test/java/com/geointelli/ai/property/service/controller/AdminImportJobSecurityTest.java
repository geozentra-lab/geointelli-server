package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.config.AuthoritiesConverter;
import com.geointelli.ai.property.service.config.SecurityConfig;
import com.geointelli.ai.property.service.dto.ImportJobDTO;
import com.geointelli.ai.property.service.dto.ImportJobPageDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.service.AdminImportJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real security filter chain (SecurityConfig + AuthoritiesConverter) in front of the admin
 * import-job endpoints. The JwtDecoder is mocked: each test token decodes to a JWT with chosen realm roles.
 */
@WebMvcTest(AdminImportJobController.class)
@Import({SecurityConfig.class, AuthoritiesConverter.class})
public class AdminImportJobSecurityTest {

    private static final String LIST_URL = "/api/admin/import-jobs";
    private static final String DETAIL_URL = "/api/admin/import-jobs/7";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AdminImportJobService adminImportJobService;

    @MockBean
    JwtDecoder jwtDecoder;

    private static Jwt jwtWithRealmRoles(String token, List<String> roles) {
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
        when(jwtDecoder.decode("admin-token"))
                .thenReturn(jwtWithRealmRoles("admin-token", List.of("default-roles-geozentra", "admin")));
        when(jwtDecoder.decode("user-token"))
                .thenReturn(jwtWithRealmRoles("user-token", List.of("default-roles-geozentra", "offline_access")));
        when(jwtDecoder.decode("bad-token")).thenThrow(new BadJwtException("invalid signature"));

        ImportJobPageDTO page = new ImportJobPageDTO();
        page.setJobs(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));
        when(adminImportJobService.listJobs(anyInt(), anyInt())).thenReturn(page);
        when(adminImportJobService.getJob(anyLong())).thenReturn(new ImportJobDTO());
    }

    @Nested
    @DisplayName("Admin import-job endpoints security")
    class Security {

        @Test
        @DisplayName("When no bearer token is sent, then return 401 without calling the service")
        void whenNoToken_thenReturn401() throws Exception {
            mockMvc.perform(get(LIST_URL)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(DETAIL_URL)).andExpect(status().isUnauthorized());
            verifyNoInteractions(adminImportJobService);
        }

        @Test
        @DisplayName("When the token is invalid, then return 401 without calling the service")
        void whenInvalidToken_thenReturn401() throws Exception {
            mockMvc.perform(get(LIST_URL).header("Authorization", "Bearer bad-token"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(adminImportJobService);
        }

        @Test
        @DisplayName("When the user has no admin realm role, then return 403 without calling the service")
        void whenUserIsNotAdmin_thenReturn403() throws Exception {
            mockMvc.perform(get(LIST_URL).header("Authorization", "Bearer user-token"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(DETAIL_URL).header("Authorization", "Bearer user-token"))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(adminImportJobService);
        }

        @Test
        @DisplayName("When the user has the admin realm role, then return 200")
        void whenUserIsAdmin_thenReturn200() throws Exception {
            mockMvc.perform(get(LIST_URL).header("Authorization", "Bearer admin-token"))
                    .andExpect(status().isOk());
            mockMvc.perform(get(DETAIL_URL).header("Authorization", "Bearer admin-token"))
                    .andExpect(status().isOk());
            verify(adminImportJobService).listJobs(0, 20);
            verify(adminImportJobService).getJob(7L);
        }
    }
}
