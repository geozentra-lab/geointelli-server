package com.geointelli.ai.property.service.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.advice.LenderExceptionHandler;
import com.geointelli.ai.property.service.dto.LenderMemberLookupDTO;
import com.geointelli.ai.property.service.dto.UserDTO;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import com.geointelli.ai.property.service.service.RealtorLicenseVerificationService;
import com.geointelli.ai.property.service.service.UserService;

/** HTTP binding of the two email lookups; the route rules are in SecurityPolicyTest. */
class UserEmailLookupControllerTest {
    private final UserService users = mock(UserService.class);
    private final RealtorLicenseVerificationService licenses = mock(RealtorLicenseVerificationService.class);
    private final LenderOrganizationService organizations = mock(LenderOrganizationService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        User owner = new User();
        owner.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(owner);
        mvc = MockMvcBuilders.standaloneSetup(new AdminUserController(users, licenses),
                new LenderOrganizationController(organizations, currentUser))
            .setControllerAdvice(new LenderExceptionHandler(), new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().registerModule(new JavaTimeModule())))
            .build();
    }

    @Test
    void adminGetsTheAccountWithItsLicenseStatus() throws Exception {
        UserDTO user = new UserDTO();
        user.setId(41L);
        user.setEmail("jane@example.com");
        user.setKeycloakUserId("kc-41");
        when(users.findByEmail("jane@example.com")).thenReturn(user);
        when(licenses.latestStatusOf(41L)).thenReturn(Optional.of(RealtorLicenseVerificationStatus.VERIFIED));

        mvc.perform(get("/api/admin/users").param("email", "jane@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(41))
            .andExpect(jsonPath("$.keycloakUserId").value("kc-41"))
            .andExpect(jsonPath("$.realtorLicenseStatus").value("VERIFIED"));
    }

    @Test
    void adminErrors() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isBadRequest());
        when(users.findByEmail("nobody@example.com"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No account uses this email"));
        mvc.perform(get("/api/admin/users").param("email", "nobody@example.com")).andExpect(status().isNotFound());
        when(users.findByEmail("nobody")).thenThrow(new IllegalArgumentException("email must be an email address"));
        mvc.perform(get("/api/admin/users").param("email", "nobody")).andExpect(status().isBadRequest());
        verify(licenses, never()).latestStatusOf(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void ownerLooksUpAColleagueAsTheAuthenticatedActor() throws Exception {
        when(organizations.lookupMember(7L, 10L, "colleague@bank.example"))
            .thenReturn(new LenderMemberLookupDTO(9L, "colleague@bank.example", "Cleo", "Banks", false));

        mvc.perform(get("/api/lender/organizations/10/members/lookup").param("email", "colleague@bank.example"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(9))
            .andExpect(jsonPath("$.firstName").value("Cleo"))
            .andExpect(jsonPath("$.member").value(false));
    }

    @Test
    void ownerLookupErrors() throws Exception {
        mvc.perform(get("/api/lender/organizations/10/members/lookup")).andExpect(status().isBadRequest());
        when(organizations.lookupMember(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(11L),
                anyString())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the organization owner may manage members"));
        mvc.perform(get("/api/lender/organizations/11/members/lookup").param("email", "colleague@bank.example"))
            .andExpect(status().isForbidden());
    }
}
