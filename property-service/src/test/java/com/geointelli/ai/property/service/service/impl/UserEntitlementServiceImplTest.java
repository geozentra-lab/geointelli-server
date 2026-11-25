package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.config.AuthoritiesConverter;
import com.geointelli.ai.property.service.dto.LenderOrganizationDTO;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.entity.UserPersona;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;

class UserEntitlementServiceImplTest {
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final LenderOrganizationService organizations = mock(LenderOrganizationService.class);
    private final UserEntitlementServiceImpl service = new UserEntitlementServiceImpl(current, organizations);

    private void login(String... roles) {
        var claims = Map.<String, Object>of("realm_access", Map.of("roles", List.of(roles)));
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("owner").claims(c -> c.putAll(claims))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, new AuthoritiesConverter().convert(claims)));
        User user = new User();
        user.setId(7L);
        user.setPersona(UserPersona.REALTOR);
        when(current.getCurrentUserEntity()).thenReturn(user);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void selectedPersonaDoesNotGrantProfessionalAccess() {
        login();
        var result = service.getEntitlements();
        assertThat(result.persona()).isEqualTo(UserPersona.REALTOR);
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
