package com.geointelli.ai.property.service.service;

import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import com.geointelli.ai.property.service.config.ApplicationRoles;
import com.geointelli.ai.property.service.entity.User;

/**
 * Onboarding of SCRUM-204. The account type is the user's professional realm role in Keycloak (investor, realtor or
 * lender), one per account; the legacy persona is never read. A user without one is a home buyer.
 */
public interface OnboardingService {

    /** The professional realm roles, in display order. */
    List<String> PROFESSIONAL_ROLES = List.of(ApplicationRoles.INVESTOR, ApplicationRoles.REALTOR, ApplicationRoles.LENDER);

    /**
     * Professional roles the user holds now. For each role, GeoZentra's latest grant or revocation decides when it is
     * newer than the token (the token was issued before it); otherwise the token decides, which also covers roles
     * assigned by hand in the Keycloak console.
     */
    Set<String> heldProfessionalRoles(User user, Authentication authentication);

    /**
     * Refuses (409) a request for {@code role} when the account already has, or waits for, another professional role:
     * another role in {@code held}, a pending request for another role, or (for investor and lender) a realtor
     * license under review. The same role is checked by the caller.
     */
    void requireNoOtherProfessionalRole(User user, String role, Set<String> held);

    /** Marks the onboarding page as answered; the first time is kept. */
    void markCompleted(User user);

    /** Home buyer choice: marks the current user's onboarding as answered without a professional role. */
    void completeWithoutRole();
}
