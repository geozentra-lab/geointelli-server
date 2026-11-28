package com.geointelli.ai.property.service.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import com.geointelli.ai.property.service.dto.UserSuggestionDTO;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import com.geointelli.ai.property.service.service.RealtorLicenseVerificationService;
import com.geointelli.ai.property.service.service.UserService;

/**
 * HTTP binding of the two email lookups and the two searches (admin, lender organization owner); the route rules are
 * in SecurityPolicyTest.
 */
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
    void adminGetsSuggestionsTenByDefault() throws Exception {
        when(users.search("dan", 10)).thenReturn(List.of(new UserSuggestionDTO(9L, "dana@bank.example", "Dana", "Jonas")));
        when(users.search("dan", 3)).thenReturn(List.of());

        mvc.perform(get("/api/admin/users/search").param("q", "dan"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(9))
            .andExpect(jsonPath("$[0].email").value("dana@bank.example"))
            .andExpect(jsonPath("$[0].firstName").value("Dana"))
            .andExpect(jsonPath("$[0].lastName").value("Jonas"))
            .andExpect(jsonPath("$[0].keycloakUserId").doesNotExist());
        mvc.perform(get("/api/admin/users/search").param("q", "dan").param("size", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void adminSearchErrors() throws Exception {
        mvc.perform(get("/api/admin/users/search")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users/search").param("q", "dan").param("size", "many"))
            .andExpect(status().isBadRequest());
        when(users.search("d", 10)).thenThrow(new IllegalArgumentException("q must have at least 2 characters"));
        mvc.perform(get("/api/admin/users/search").param("q", "d"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("q must have at least 2 characters"));
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

    @Test
    void ownerGetsSuggestionsWithMembershipAsTheAuthenticatedActorTenByDefault() throws Exception {
        when(organizations.searchMembers(7L, 10L, "cle", 10)).thenReturn(List.of(
            new LenderMemberLookupDTO(8L, "clement@bank.example", "Clement", "Rey", true),
            new LenderMemberLookupDTO(9L, "cleo@bank.example", "Cleo", "Banks", false)));
        when(organizations.searchMembers(7L, 10L, "cle", 3)).thenReturn(List.of());

        mvc.perform(get("/api/lender/organizations/10/members/search").param("q", "cle"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].userId").value(8))
            .andExpect(jsonPath("$[0].member").value(true))
            .andExpect(jsonPath("$[1].userId").value(9))
            .andExpect(jsonPath("$[1].email").value("cleo@bank.example"))
            .andExpect(jsonPath("$[1].firstName").value("Cleo"))
            .andExpect(jsonPath("$[1].lastName").value("Banks"))
            .andExpect(jsonPath("$[1].member").value(false))
            .andExpect(jsonPath("$[1].keycloakUserId").doesNotExist());
        mvc.perform(get("/api/lender/organizations/10/members/search").param("q", "cle").param("size", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void ownerSearchErrors() throws Exception {
        mvc.perform(get("/api/lender/organizations/10/members/search")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/lender/organizations/10/members/search").param("q", "cle").param("size", "many"))
            .andExpect(status().isBadRequest());
        when(organizations.searchMembers(7L, 10L, "c", 10))
            .thenThrow(new IllegalArgumentException("q must have at least 2 characters"));
        mvc.perform(get("/api/lender/organizations/10/members/search").param("q", "c"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("q must have at least 2 characters"));
        when(organizations.searchMembers(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(11L),
                anyString(), anyInt())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the organization owner may manage members"));
        mvc.perform(get("/api/lender/organizations/11/members/search").param("q", "cle"))
            .andExpect(status().isForbidden());
        when(organizations.searchMembers(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(12L),
                anyString(), anyInt())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Organization not found"));
        mvc.perform(get("/api/lender/organizations/12/members/search").param("q", "cle"))
            .andExpect(status().isNotFound());
    }
}
