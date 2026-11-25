package com.geointelli.ai.property.service.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.config.ApplicationRoles;
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
import com.geointelli.ai.property.service.service.OnboardingService;

/** One professional realm role per account, and the onboarding page state (SCRUM-204). */
@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private final RoleGrantEventRepository events;
    private final RoleRequestRepository requests;
    private final RealtorLicenseVerificationRepository licenses;
    private final UserRepository users;
    private final CurrentUserService currentUser;

    @Override
    public Set<String> heldProfessionalRoles(User user, Authentication authentication) {
        Instant issuedAt = authentication != null && authentication.getPrincipal() instanceof Jwt jwt
            ? jwt.getIssuedAt() : null;
        Set<String> held = new TreeSet<>();
        for (String role : PROFESSIONAL_ROLES) {
            boolean inToken = authentication != null && ApplicationRoles.hasRole(authentication, role);
            Optional<RoleGrantEvent> latest = events.findFirstByUserIdAndRoleOrderByIdDesc(user.getId(), role);
            boolean eventIsNewer = latest.isPresent() && (issuedAt == null || latest.get().getCreatedAt() == null
                || latest.get().getCreatedAt().isAfter(issuedAt));
            if (eventIsNewer ? latest.get().getAction() == RoleGrantAction.GRANT : inToken) {
                held.add(role);
            }
        }
        return held;
    }

    @Override
    public void requireNoOtherProfessionalRole(User user, String role, Set<String> held) {
        Set<String> otherHeld = new TreeSet<>(held);
        otherHeld.remove(role);
        if (!otherHeld.isEmpty()) {
            throw conflict("Your account is already " + article(otherHeld) + " account. An account has one "
                + "professional role (investor, realtor or lender): ask a GeoZentra admin to change it");
        }
        Set<String> otherPending = new TreeSet<>(requests.findRolesByUserIdAndStatus(user.getId(), RoleRequestStatus.PENDING));
        otherPending.remove(role);
        if (!otherPending.isEmpty()) {
            throw conflict("Your " + String.join(", ", otherPending) + " request is pending. An account has one "
                + "professional role: cancel that request first (POST /api/users/me/role-requests/{id}/cancel)");
        }
        if (!ApplicationRoles.REALTOR.equals(role) && licenses.existsByUserIdAndStatusIn(user.getId(),
                List.of(RealtorLicenseVerificationStatus.PENDING_REVIEW))) {
            throw conflict("Your realtor license is under review. An account has one professional role: wait for the "
                + "decision, or ask a GeoZentra admin");
        }
    }

    @Override
    @Transactional
    public void markCompleted(User user) {
        if (user.getOnboardingCompletedAt() == null) {
            user.setOnboardingCompletedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
            users.save(user);
        }
    }

    @Override
    @Transactional
    public void completeWithoutRole() {
        markCompleted(currentUser.getCurrentUserEntity());
    }

    private static String article(Set<String> roles) {
        String names = String.join(", ", roles);
        return (names.startsWith("investor") ? "an " : "a ") + names;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
