package com.geointelli.ai.notification.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/** Prevents notification ownership from falling back to names or anonymous identities. */
class CurrentUserServiceImplTest {
    private final CurrentUserServiceImpl service = new CurrentUserServiceImpl();

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsSubjectRatherThanPreferredUsername() {
        authenticate("keycloak-subject");
        assertEquals("keycloak-subject", service.getSubject());
    }

    @Test
    void rejectsMissingAuthentication() {
        SecurityContextHolder.clearContext();
        assertUnauthorized();
    }

    @Test
    void rejectsAnonymousAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "owner-a",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertUnauthorized();
    }

    @Test
    void rejectsNonJwtAuthenticationEvenWithTheSameName() {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("owner-a", "unused", List.of()));
        assertUnauthorized();
    }

    @Test
    void rejectsUnauthenticatedJwt() {
        authenticate("owner-a");
        SecurityContextHolder.getContext().getAuthentication().setAuthenticated(false);
        assertUnauthorized();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsUnusableSubjects(String subject) {
        authenticate(subject);
        assertUnauthorized();
    }

    @Test
    void rejectsSubjectsThatCannotFitTheRecipientColumn() {
        authenticate("a".repeat(256));
        assertUnauthorized();
    }

    private void authenticate(String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token").header("alg", "RS256")
            .claim("preferred_username", "different-login-name");
        if (subject != null) builder.subject(subject);
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(builder.build(), List.of()));
    }

    private void assertUnauthorized() {
        assertEquals(HttpStatus.UNAUTHORIZED,
            assertThrows(ResponseStatusException.class, service::getSubject).getStatusCode());
    }
}
