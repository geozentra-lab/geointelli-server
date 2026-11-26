package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.config.AuthoritiesConverter;
import com.geointelli.ai.property.service.config.OnboardingProperties;
import com.geointelli.ai.property.service.dto.RoleRequestCreateRequest;
import com.geointelli.ai.property.service.dto.RoleRequestDTO;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;
import com.geointelli.ai.property.service.entity.RoleGrantAction;
import com.geointelli.ai.property.service.entity.RoleGrantEvent;
import com.geointelli.ai.property.service.entity.RoleGrantSource;
import com.geointelli.ai.property.service.entity.RoleRequest;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.exception.KeycloakRoleException;
import com.geointelli.ai.property.service.repository.RealtorLicenseVerificationRepository;
import com.geointelli.ai.property.service.repository.RoleGrantEventRepository;
import com.geointelli.ai.property.service.repository.RoleRequestRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.RoleGrantService;

/** Role requests of SCRUM-204: what can be requested, and decisions that call Keycloak first. */
class RoleRequestServiceImplTest {

    private final RoleRequestRepository requests = mock(RoleRequestRepository.class);
    private final RoleGrantEventRepository events = mock(RoleGrantEventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final RoleGrantService roleGrants = mock(RoleGrantService.class);
    private final RealtorLicenseVerificationRepository licenses = mock(RealtorLicenseVerificationRepository.class);
    private final OnboardingProperties onboarding = new OnboardingProperties();
    private final OnboardingServiceImpl accountRoles =
        new OnboardingServiceImpl(events, requests, licenses, users, currentUser);
    private final RoleRequestServiceImpl service =
        new RoleRequestServiceImpl(requests, users, currentUser, roleGrants, onboarding, accountRoles);

    private User applicant;
    private User admin;

    @BeforeEach
    void setUp() {
        applicant = user(7L, "kc-applicant", "applicant@test.invalid");
        admin = user(1L, "kc-admin", "admin@test.invalid");
        when(currentUser.getCurrentUserEntity()).thenReturn(applicant);
        when(users.findById(7L)).thenReturn(Optional.of(applicant));
        when(events.findFirstByUserIdAndRoleOrderByIdDesc(any(), any())).thenReturn(Optional.empty());
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> {
            RoleRequest row = invocation.getArgument(0);
            if (row.getId() == null) row.setId(50L);
            return row;
        });
        login();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static User user(long id, String keycloakId, String email) {
        User user = new User();
        user.setId(id);
        user.setKeycloakUserId(keycloakId);
        user.setEmail(email);
        return user;
    }

    private void login(String... roles) {
        var claims = Map.<String, Object>of("realm_access", Map.of("roles", List.of(roles)));
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-applicant")
            .claims(c -> c.putAll(claims)).build();
        SecurityContextHolder.getContext()
            .setAuthentication(new JwtAuthenticationToken(jwt, new AuthoritiesConverter().convert(claims)));
    }

    private RoleRequest pending(String role) {
        RoleRequest row = new RoleRequest();
        row.setId(50L);
        row.setUserId(7L);
        row.setRequestedRole(role);
        row.setOrganizationName("lender".equals(role) ? "Acme Mortgage" : null);
        when(requests.findByIdForUpdate(50L)).thenReturn(Optional.of(row));
        return row;
    }

    @Test
    @DisplayName("By default every request waits for an admin: PENDING, Keycloak not called")
    void adminApprovalByDefault() {
        RoleRequestDTO lender = service.create(new RoleRequestCreateRequest(" Lender ", " Acme Mortgage ", " Hi "));

        assertThat(lender.status()).isEqualTo(RoleRequestStatus.PENDING);
        assertThat(lender.role()).isEqualTo("lender");
        assertThat(lender.organizationName()).isEqualTo("Acme Mortgage");
        assertThat(lender.note()).isEqualTo("Hi");
        assertThat(lender.decidedAt()).isNull();
        assertThat(service.create(new RoleRequestCreateRequest("investor", null, null)).status())
            .isEqualTo(RoleRequestStatus.PENDING);
        verifyNoInteractions(roleGrants);
    }

    @Test
    @DisplayName("Lender listed as self-service: granted at once, the request APPROVED with its details")
    void lenderSelfService() {
        onboarding.setSelfServiceRoles(new java.util.ArrayList<>(List.of("lender")));
        RoleRequestDTO created = service.create(new RoleRequestCreateRequest(" Lender ", " Acme Mortgage ", " Hi "));

        assertThat(created.status()).isEqualTo(RoleRequestStatus.APPROVED);
        assertThat(created.role()).isEqualTo("lender");
        assertThat(created.organizationName()).isEqualTo("Acme Mortgage");
        assertThat(created.note()).isEqualTo("Hi");
        assertThat(created.decidedAt()).isNotNull();
        verify(roleGrants).grant(eq(applicant), eq("lender"), eq(RoleGrantSource.SELF_SERVICE), eq(50L), any(),
            isNull());
    }

    @Test
    @DisplayName("Realtor, admin and unknown roles are 400; a lender request needs no organization")
    void invalidRequests() {
        assertThatThrownBy(() -> service.create(new RoleRequestCreateRequest("realtor", null, null)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("realtor-license");
        assertThatThrownBy(() -> service.create(new RoleRequestCreateRequest("admin", null, null)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("investor or lender");
        verify(requests, never()).saveAndFlush(any());

        assertThat(service.create(new RoleRequestCreateRequest("lender", "  ", null)).organizationName()).isNull();
    }

    @Test
    @DisplayName("409 when the token already gives the feature, when admin, or when a grant is newer than the token")
    void alreadyHasAccess() {
        login("lender");
        assertConflict(() -> service.create(new RoleRequestCreateRequest("lender", "Acme", null)), "already have");
        login("admin");
        assertConflict(() -> service.create(new RoleRequestCreateRequest("investor", null, null)), "already have");

        login();
        RoleGrantEvent grant = new RoleGrantEvent();
        grant.setAction(RoleGrantAction.GRANT);
        when(events.findFirstByUserIdAndRoleOrderByIdDesc(7L, "investor")).thenReturn(Optional.of(grant));
        assertConflict(() -> service.create(new RoleRequestCreateRequest("investor", null, null)),
            "refresh your access token");
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("One professional role per account: another held role, pending request or pending license gives 409")
    void oneProfessionalRole() {
        login("lender");
        assertConflict(() -> service.create(new RoleRequestCreateRequest("investor", null, null)),
            "already a lender account");

        login();
        when(requests.findRolesByUserIdAndStatus(7L, RoleRequestStatus.PENDING)).thenReturn(List.of("lender"));
        assertConflict(() -> service.create(new RoleRequestCreateRequest("investor", null, null)),
            "lender request is pending");

        when(requests.findRolesByUserIdAndStatus(7L, RoleRequestStatus.PENDING)).thenReturn(List.of());
        when(licenses.existsByUserIdAndStatusIn(7L, List.of(RealtorLicenseVerificationStatus.PENDING_REVIEW)))
            .thenReturn(true);
        assertConflict(() -> service.create(new RoleRequestCreateRequest("lender", null, null)),
            "realtor license is under review");
        verify(requests, never()).saveAndFlush(any());
        assertThat(applicant.getOnboardingCompletedAt()).isNull();
    }

    @Test
    @DisplayName("A role revoked since the token was issued no longer counts: the other role can be requested")
    void revokedRoleFreesTheAccount() {
        login("lender");
        RoleGrantEvent revoke = new RoleGrantEvent();
        revoke.setAction(RoleGrantAction.REVOKE);
        when(events.findFirstByUserIdAndRoleOrderByIdDesc(7L, "lender")).thenReturn(Optional.of(revoke));

        assertThat(service.create(new RoleRequestCreateRequest("investor", null, null)).status())
            .isEqualTo(RoleRequestStatus.PENDING);
    }

    @Test
    @DisplayName("Choosing investor or lender answers the onboarding page; the first time is kept")
    void requestCompletesOnboarding() {
        service.create(new RoleRequestCreateRequest("lender", null, null));
        Instant first = applicant.getOnboardingCompletedAt();
        assertThat(first).isNotNull();
        verify(users).save(applicant);

        accountRoles.markCompleted(applicant);
        assertThat(applicant.getOnboardingCompletedAt()).isEqualTo(first);
        verify(users, times(1)).save(applicant);
    }

    @Test
    @DisplayName("409 when a request for the role is already pending, also when a concurrent one wins the index")
    void onePendingPerRole() {
        when(requests.existsByUserIdAndRequestedRoleAndStatus(7L, "investor", RoleRequestStatus.PENDING))
            .thenReturn(true);
        assertConflict(() -> service.create(new RoleRequestCreateRequest("investor", null, null)), "already pending");

        when(requests.existsByUserIdAndRequestedRoleAndStatus(7L, "lender", RoleRequestStatus.PENDING))
            .thenReturn(false);
        doThrow(new DataIntegrityViolationException("ux_role_requests_one_pending")).when(requests).saveAndFlush(any());
        assertConflict(() -> service.create(new RoleRequestCreateRequest("lender", "Acme", null)), "already pending");
    }

    @Test
    @DisplayName("Investor listed as self-service: granted at once and APPROVED")
    void investorSelfService() {
        onboarding.setSelfServiceRoles(new java.util.ArrayList<>(List.of("investor")));
        RoleRequestDTO created = service.create(new RoleRequestCreateRequest("investor", null, null));

        assertThat(created.status()).isEqualTo(RoleRequestStatus.APPROVED);
        assertThat(created.decidedAt()).isNotNull();
        assertThat(created.decisionReason()).isEqualTo(RoleRequestServiceImpl.SELF_SERVICE_REASON);
        verify(roleGrants).grant(eq(applicant), eq("investor"), eq(RoleGrantSource.SELF_SERVICE), eq(50L), any(),
            isNull());
    }

    @Test
    @DisplayName("Self-service with Keycloak down: the request stays PENDING for an admin")
    void selfServiceKeycloakDown() {
        onboarding.setSelfServiceRoles(new java.util.ArrayList<>(List.of("investor", "lender")));
        when(roleGrants.grant(any(), any(), any(), any(), any(), any()))
            .thenThrow(KeycloakRoleException.unavailable("Keycloak could not be reached; nothing was changed"));

        RoleRequestDTO created = service.create(new RoleRequestCreateRequest("lender", null, null));

        assertThat(created.status()).isEqualTo(RoleRequestStatus.PENDING);
        assertThat(created.decidedAt()).isNull();
    }

    @Test
    @DisplayName("A role removed from the self-service list waits for an admin")
    void notSelfServiceWaits() {
        onboarding.setSelfServiceRoles(new java.util.ArrayList<>(List.of("investor")));

        assertThat(service.create(new RoleRequestCreateRequest("lender", "Acme", null)).status())
            .isEqualTo(RoleRequestStatus.PENDING);
        verifyNoInteractions(roleGrants);
    }

    @Test
    @DisplayName("The self-service list refuses realtor and admin at startup")
    void selfServiceListValidated() {
        OnboardingProperties properties = new OnboardingProperties();
        properties.setSelfServiceRoles(new java.util.ArrayList<>(List.of(" Lender ", "INVESTOR")));
        properties.validate();
        assertThat(properties.getSelfServiceRoles()).containsExactly("lender", "investor");

        properties.setSelfServiceRoles(new java.util.ArrayList<>(List.of("realtor")));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setSelfServiceRoles(new java.util.ArrayList<>(List.of("admin")));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Approval grants in Keycloak first, then records the decision with the admin")
    void approveGrantsThenDecides() {
        when(currentUser.getCurrentUserEntity()).thenReturn(admin);
        RoleRequest row = pending("lender");

        var decided = service.approve(50L, " Checked NMLS ");

        var order = inOrder(roleGrants, requests);
        order.verify(roleGrants).grant(applicant, "lender", RoleGrantSource.ROLE_REQUEST, 50L, "Checked NMLS", 1L);
        order.verify(requests).saveAndFlush(row);
        assertThat(decided.status()).isEqualTo(RoleRequestStatus.APPROVED);
        assertThat(decided.decidedByUserId()).isEqualTo(1L);
        assertThat(decided.decisionReason()).isEqualTo("Checked NMLS");
        assertThat(decided.applicantEmail()).isEqualTo("applicant@test.invalid");
    }

    @Test
    @DisplayName("Approval with Keycloak down is 503 and the request stays PENDING")
    void approveKeycloakDown() {
        when(currentUser.getCurrentUserEntity()).thenReturn(admin);
        RoleRequest row = pending("lender");
        when(roleGrants.grant(any(), any(), any(), any(), any(), any()))
            .thenThrow(KeycloakRoleException.unavailable("Keycloak could not be reached; nothing was changed"));

        assertThatThrownBy(() -> service.approve(50L, null))
            .isInstanceOfSatisfying(KeycloakRoleException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(row.getStatus()).isEqualTo(RoleRequestStatus.PENDING);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A decided request cannot be decided or cancelled again (409); a missing one is 404")
    void decidedOnce() {
        when(currentUser.getCurrentUserEntity()).thenReturn(admin);
        RoleRequest row = pending("investor");
        row.setStatus(RoleRequestStatus.REJECTED);
        assertConflict(() -> service.approve(50L, null), "already decided");
        assertConflict(() -> service.reject(50L, "no"), "already decided");
        verifyNoInteractions(roleGrants);

        when(requests.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approve(99L, null))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("A rejection needs a reason and never calls Keycloak")
    void rejectNeedsReason() {
        when(currentUser.getCurrentUserEntity()).thenReturn(admin);
        pending("lender");
        assertThatThrownBy(() -> service.reject(50L, " ")).isInstanceOf(IllegalArgumentException.class);

        var decided = service.reject(50L, "Organization not found in NMLS");

        assertThat(decided.status()).isEqualTo(RoleRequestStatus.REJECTED);
        assertThat(decided.decisionReason()).isEqualTo("Organization not found in NMLS");
        verifyNoInteractions(roleGrants);
    }

    @Test
    @DisplayName("The applicant cancels a pending request; another user's request is 404")
    void cancelOwnOnly() {
        RoleRequest row = pending("investor");
        assertThat(service.cancelMine(50L).status()).isEqualTo(RoleRequestStatus.CANCELLED);
        assertThat(row.getDecidedByUserId()).isEqualTo(7L);

        RoleRequest foreign = pending("investor");
        foreign.setUserId(8L);
        assertThatThrownBy(() -> service.cancelMine(50L))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("The review queue checks paging and the role filter")
    void listValidation() {
        assertThatThrownBy(() -> service.list(null, null, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(null, "realtor", 0, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String text) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getReason()).contains(text);
        });
    }
}
