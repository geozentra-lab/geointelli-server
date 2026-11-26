package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.config.AuthoritiesConverter;
import com.geointelli.ai.property.service.dto.LenderOrganizationDTO;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.entity.UserPersona;
import com.geointelli.ai.property.service.repository.RoleRequestRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import com.geointelli.ai.property.service.service.RealtorLicenseVerificationService;

class UserEntitlementServiceImplTest {
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final LenderOrganizationService organizations = mock(LenderOrganizationService.class);
    private final RoleRequestRepository roleRequests = mock(RoleRequestRepository.class);
    private final RealtorLicenseVerificationService licenses = mock(RealtorLicenseVerificationService.class);
    private final UserEntitlementServiceImpl service =
            new UserEntitlementServiceImpl(current, organizations, roleRequests, licenses);
    private User user;

    private void login(String... roles) {
        var claims = Map.<String, Object>of("realm_access", Map.of("roles", List.of(roles)));
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("owner").claims(c -> c.putAll(claims))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, new AuthoritiesConverter().convert(claims)));
        user = new User();
        user.setId(7L);
        user.setPersona(UserPersona.REALTOR);
        when(current.getCurrentUserEntity()).thenReturn(user);
        when(licenses.latestStatusOf(7L)).thenReturn(Optional.empty());
    }

    @Test
    void onboardingNotAnsweredTellsTheFrontendToShowThePage() {
        login();
        user.setOnboardingCompletedAt(null);
        var result = service.getEntitlements();
        assertThat(result.onboardingCompleted()).isFalse();
        assertThat(result.onboardingCompletedAt()).isNull();
        assertThat(result.pendingRoles()).isEmpty();
    }

    @Test
    void pendingRequestsAndPendingLicenseAreListedAsPendingRoles() {
        login();
        Instant chosen = Instant.parse("2026-11-25T10:00:00Z");
        user.setOnboardingCompletedAt(chosen);
        when(roleRequests.findRolesByUserIdAndStatus(7L, RoleRequestStatus.PENDING)).thenReturn(List.of("lender"));
        when(licenses.latestStatusOf(7L)).thenReturn(Optional.of(RealtorLicenseVerificationStatus.PENDING_REVIEW));
        var result = service.getEntitlements();
        assertThat(result.onboardingCompleted()).isTrue();
        assertThat(result.onboardingCompletedAt()).isEqualTo(chosen);
        assertThat(result.pendingRoles()).containsExactly("lender", "realtor");
        // pending is not granted: the features stay off until the role is in the token
        assertThat(result.features().lender()).isFalse();
        assertThat(result.features().realtorCrm()).isFalse();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void legacyPersonaColumnDoesNotGrantProfessionalAccess() {
        login();
        var result = service.getEntitlements();
        assertThat(result.roles()).containsExactly("user");
        assertThat(result.features().normalUser()).isTrue();
        assertThat(result.features().investment()).isFalse();
        assertThat(result.features().realtorCrm()).isFalse();
        assertThat(result.features().lender()).isFalse();
        assertThat(result.features().administration()).isFalse();
        assertThat(result.subscriptionPolicy()).isEqualTo("NOT_CONFIGURED");
        verifyNoInteractions(organizations);
    }

    @Test
    void rolesCombineWithoutDependingOnPersona() {
        login("investor", "realtor");
        var result = service.getEntitlements();
        assertThat(result.features().investment()).isTrue();
        assertThat(result.features().realtorCrm()).isTrue();
        assertThat(result.features().lender()).isFalse();
        verifyNoInteractions(organizations);
    }

    @Test
    void lenderAndAdminSeeOnlyOrganizationsReturnedForTheCurrentUser() {
        var visible = List.of(new LenderOrganizationDTO(11L, "My lender", 7L));
        when(organizations.list(7L)).thenReturn(visible);
        login("lender");
        assertThat(service.getEntitlements().lenderOrganizations()).isEqualTo(visible);
        login("admin");
        var admin = service.getEntitlements();
        assertThat(admin.features().investment()).isTrue();
        assertThat(admin.features().realtorCrm()).isTrue();
        assertThat(admin.features().administration()).isTrue();
        assertThat(admin.lenderOrganizations()).isEqualTo(visible);
        verify(organizations, times(2)).list(7L);
        verifyNoMoreInteractions(organizations);
    }

    @Test
    void missingAuthenticationReturns401BeforeDatabaseAccess() {
        assertThatThrownBy(service::getEntitlements).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(current, organizations);
    }
}
