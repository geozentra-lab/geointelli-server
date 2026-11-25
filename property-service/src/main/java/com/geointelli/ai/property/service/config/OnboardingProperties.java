package com.geointelli.ai.property.service.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Onboarding rules of SCRUM-204. {@code selfServiceRoles} lists the realm roles granted as soon as the user requests
 * them, without an admin. Empty by default: every investor and lender request waits for a GeoZentra admin's approval
 * (product decision of 2026-11-25). Listing investor or lender grants it at once. Realtor is never self-service: it
 * needs an approved license.
 */
@Component
@ConfigurationProperties(prefix = "onboarding")
@Getter
@Setter
public class OnboardingProperties {

    private static final Set<String> ALLOWED = Set.of(ApplicationRoles.INVESTOR, ApplicationRoles.LENDER);

    private List<String> selfServiceRoles = new ArrayList<>();

    /** Normalises the list and refuses realtor, admin or unknown roles at startup. */
    @PostConstruct
    public void validate() {
        List<String> normalized = new ArrayList<>();
        for (String role : selfServiceRoles == null ? List.<String>of() : selfServiceRoles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String value = role.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED.contains(value)) {
                throw new IllegalStateException("onboarding.self-service-roles accepts only investor and lender, not '"
                    + role + "'");
            }
            normalized.add(value);
        }
        selfServiceRoles = normalized;
    }

    /** Whether a request for this realm role is granted at once. */
    public boolean isSelfService(String role) {
        return selfServiceRoles != null && selfServiceRoles.contains(role);
    }
}
