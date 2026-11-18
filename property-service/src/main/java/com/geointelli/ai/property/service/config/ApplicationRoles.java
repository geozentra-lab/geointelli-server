package com.geointelli.ai.property.service.config;

import org.springframework.security.core.Authentication;

/**
 * Case-sensitive Keycloak realm roles. Profile personas and client roles do not
 * grant these roles.
 */
public final class ApplicationRoles {
    public static final String ADMIN = "admin";
    public static final String INVESTOR = "investor";
    public static final String REALTOR = "realtor";
    public static final String LENDER = "lender";

    private ApplicationRoles() {
    }

    /**
     * Returns whether a validated authentication has the exact realm-role
     * authority.
     */
    public static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    /**
     * Admins may use feature routes, but still need record ownership or
     * organization membership.
     */
    public static boolean canUse(Authentication authentication, String role) {
        return hasRole(authentication, ADMIN) || hasRole(authentication, role);
    }
}
