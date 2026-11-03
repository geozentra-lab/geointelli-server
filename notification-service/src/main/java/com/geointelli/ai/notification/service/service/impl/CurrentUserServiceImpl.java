package com.geointelli.ai.notification.service.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.notification.service.service.CurrentUserService;

/**
 * Uses the token subject directly; notification-service owns no copy of
 * property-service users.
 */
@Service
public class CurrentUserServiceImpl implements CurrentUserService {
    @Override
    public String getVerifiedEmail() {
        getSubject();
        JwtAuthenticationToken token = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Object emailClaim = token.getToken().getClaims().get("email");
        if (!Boolean.TRUE.equals(token.getToken().getClaims().get("email_verified"))
                || !(emailClaim instanceof String email) || email.length() > 254
                || !email.matches("[^\\s@]+@[^\\s@]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Email delivery requires a verified email in your access token");
        }
        return email;
    }

    @Override
    public String getSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid bearer token is required");
        }
        String subject = token.getToken().getSubject();
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid token subject is required");
        }
        return subject;
    }
}
