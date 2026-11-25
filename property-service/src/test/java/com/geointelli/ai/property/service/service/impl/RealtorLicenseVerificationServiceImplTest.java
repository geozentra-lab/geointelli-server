package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.config.AuthoritiesConverter;
import com.geointelli.ai.property.service.dto.RealtorLicenseSubmissionRequest;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerification;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.RealtorLicenseVerificationRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.OnboardingService;

/** License submission and the one professional role per account rule (SCRUM-204). */
class RealtorLicenseVerificationServiceImplTest {

    private final RealtorLicenseVerificationRepository verifications = mock(RealtorLicenseVerificationRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final OnboardingService accountRoles = mock(OnboardingService.class);
    private final RealtorLicenseVerificationServiceImpl service =
        new RealtorLicenseVerificationServiceImpl(verifications, currentUser, accountRoles);
    private final RealtorLicenseSubmissionRequest request =
        new RealtorLicenseSubmissionRequest("Ann Agent", "fl", "SL123", null, null, null);
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
        when(verifications.saveAndFlush(any())).thenAnswer(call -> {
            RealtorLicenseVerification row = call.getArgument(0);
            row.setId(40L);
            return row;
        });
        var claims = Map.<String, Object>of("realm_access", Map.of("roles", List.of("lender")));
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-7").claims(c -> c.putAll(claims)).build();
        SecurityContextHolder.getContext()
            .setAuthentication(new JwtAuthenticationToken(jwt, new AuthoritiesConverter().convert(claims)));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A lender account cannot submit a realtor license (409); nothing is saved, onboarding unchanged")
    void otherProfessionalRoleRefused() {
        when(accountRoles.heldProfessionalRoles(eq(user), any())).thenReturn(Set.of("lender"));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Your account is already a lender account"))
            .when(accountRoles).requireNoOtherProfessionalRole(user, "realtor", Set.of("lender"));

        assertThatThrownBy(() -> service.submit(request)).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already a lender account");

        verify(verifications, never()).saveAndFlush(any());
        verify(accountRoles, never()).markCompleted(any());
    }

    @Test
    @DisplayName("An accepted submission is PENDING_REVIEW and answers the onboarding page")
    void submissionCompletesOnboarding() {
        var saved = service.submit(request);

        assertThat(saved.status().name()).isEqualTo("PENDING_REVIEW");
        verify(accountRoles).requireNoOtherProfessionalRole(eq(user), eq("realtor"), anySet());
        verify(accountRoles).markCompleted(user);
    }
}
