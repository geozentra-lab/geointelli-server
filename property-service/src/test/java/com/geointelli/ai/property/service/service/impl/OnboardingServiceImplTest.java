package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.config.AuthoritiesConverter;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;
import com.geointelli.ai.property.service.entity.RoleGrantAction;
import com.geointelli.ai.property.service.entity.RoleGrantEvent;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.RealtorLicenseVerificationRepository;
import com.geointelli.ai.property.service.repository.RoleGrantEventRepository;
import com.geointelli.ai.property.service.repository.RoleRequestRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;

/** One professional realm role per account and the onboarding state (SCRUM-204). */
class OnboardingServiceImplTest {

    private static final Instant ISSUED = Instant.parse("2026-11-25T10:00:00Z");

    private final RoleGrantEventRepository events = mock(RoleGrantEventRepository.class);
    private final RoleRequestRepository requests = mock(RoleRequestRepository.class);
    private final RealtorLicenseVerificationRepository licenses = mock(RealtorLicenseVerificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final OnboardingServiceImpl service = new OnboardingServiceImpl(events, requests, licenses, users, currentUser);
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        when(events.findFirstByUserIdAndRoleOrderByIdDesc(any(), any())).thenReturn(Optional.empty());
    }

    private static Authentication token(String... roles) {
        var claims = Map.<String, Object>of("realm_access", Map.of("roles", List.of(roles)));
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-7").issuedAt(ISSUED)
            .claims(c -> c.putAll(claims)).build();
        return new JwtAuthenticationToken(jwt, new AuthoritiesConverter().convert(claims));
    }

    private void event(String role, RoleGrantAction action, Instant at) {
        RoleGrantEvent event = new RoleGrantEvent();
        event.setRole(role);
        event.setAction(action);
        event.setCreatedAt(at);
        when(events.findFirstByUserIdAndRoleOrderByIdDesc(7L, role)).thenReturn(Optional.of(event));
    }

    private static void assertConflict(Runnable call, String text) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getReason()).contains(text);
        });
    }

    @Test
    @DisplayName("Held roles: the token, unless GeoZentra granted or revoked the role after the token was issued")
    void heldRoles() {
        assertThat(service.heldProfessionalRoles(user, token("admin", "realtor"))).containsExactly("realtor");

        event("lender", RoleGrantAction.GRANT, ISSUED.plusSeconds(30));
        assertThat(service.heldProfessionalRoles(user, token())).containsExactly("lender");

        event("lender", RoleGrantAction.REVOKE, ISSUED.plusSeconds(30));
        assertThat(service.heldProfessionalRoles(user, token("lender"))).isEmpty();

        // a token issued after the revocation shows the role again: it was assigned by hand in Keycloak since
        event("lender", RoleGrantAction.REVOKE, ISSUED.minusSeconds(30));
        assertThat(service.heldProfessionalRoles(user, token("lender"))).containsExactly("lender");
    }

    @Test
    @DisplayName("Another held role, a pending request for another role, or a pending license gives 409")
    void oneProfessionalRole() {
        assertConflict(() -> service.requireNoOtherProfessionalRole(user, "investor", Set.of("lender")),
            "already a lender account");
        assertConflict(() -> service.requireNoOtherProfessionalRole(user, "realtor", Set.of("investor")),
            "already an investor account");

        when(requests.findRolesByUserIdAndStatus(7L, RoleRequestStatus.PENDING)).thenReturn(List.of("lender"));
        assertConflict(() -> service.requireNoOtherProfessionalRole(user, "realtor", Set.of()),
            "lender request is pending");
        service.requireNoOtherProfessionalRole(user, "lender", Set.of());

        when(requests.findRolesByUserIdAndStatus(7L, RoleRequestStatus.PENDING)).thenReturn(List.of());
        when(licenses.existsByUserIdAndStatusIn(7L, List.of(RealtorLicenseVerificationStatus.PENDING_REVIEW)))
            .thenReturn(true);
        assertConflict(() -> service.requireNoOtherProfessionalRole(user, "lender", Set.of()),
            "realtor license is under review");
        // a pending license does not block the realtor check itself (the license service has its own rule)
        service.requireNoOtherProfessionalRole(user, "realtor", Set.of());
        // the same role is not "another" role
        service.requireNoOtherProfessionalRole(user, "realtor", Set.of("realtor"));
    }

    @Test
    @DisplayName("Onboarding: the first answer time is kept; the home buyer choice marks the current user")
    void onboarding() {
        when(currentUser.getCurrentUserEntity()).thenReturn(user);

        service.completeWithoutRole();
        Instant first = user.getOnboardingCompletedAt();
        assertThat(first).isNotNull();

        service.markCompleted(user);
        assertThat(user.getOnboardingCompletedAt()).isEqualTo(first);
        verify(users, times(1)).save(user);
    }
}
