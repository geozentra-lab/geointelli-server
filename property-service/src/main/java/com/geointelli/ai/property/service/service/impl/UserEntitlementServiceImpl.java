package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.config.ApplicationRoles;
import com.geointelli.ai.property.service.dto.UserEntitlementsResponse;
import com.geointelli.ai.property.service.dto.UserEntitlementsResponse.Features;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.RoleRequestRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import com.geointelli.ai.property.service.service.RealtorLicenseVerificationService;
import com.geointelli.ai.property.service.service.UserEntitlementService;

import lombok.RequiredArgsConstructor;

/** Realm roles grant access; admin never bypasses record ownership. */
@Service
@RequiredArgsConstructor
public class UserEntitlementServiceImpl implements UserEntitlementService {
    private final CurrentUserService currentUser;
    private final LenderOrganizationService organizations;
    private final RoleRequestRepository roleRequests;
    private final RealtorLicenseVerificationService licenses;

    /** {@inheritDoc} */
    @Override
    public UserEntitlementsResponse getEntitlements() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A bearer token is required");
        User user = currentUser.getCurrentUserEntity();
        List<String> roles = new ArrayList<>(List.of("user"));
        for (String role : List.of(ApplicationRoles.ADMIN, ApplicationRoles.INVESTOR,
                ApplicationRoles.REALTOR, ApplicationRoles.LENDER)) {
            if (ApplicationRoles.hasRole(authentication, role))
                roles.add(role);
        }
        boolean lender = ApplicationRoles.canUse(authentication, ApplicationRoles.LENDER);
        Features features = new Features(true, ApplicationRoles.canUse(authentication, ApplicationRoles.INVESTOR),
                ApplicationRoles.canUse(authentication, ApplicationRoles.REALTOR), lender,
                ApplicationRoles.hasRole(authentication, ApplicationRoles.ADMIN));
        return new UserEntitlementsResponse(user.getOnboardingCompletedAt() != null,
                user.getOnboardingCompletedAt(), List.copyOf(roles), features,
                pendingRoles(user.getId()),
                lender ? organizations.list(user.getId()) : List.of(), "NOT_CONFIGURED",
                "Personal records require ownership. Lender records require organization membership; "
                        + "only the organization owner manages members. Admin does not bypass these checks.");
    }

    /** Roles waiting for an admin (SCRUM-204): pending role requests, plus realtor while a license is pending. */
    private List<String> pendingRoles(Long userId) {
        TreeSet<String> pending = new TreeSet<>(roleRequests.findRolesByUserIdAndStatus(userId, RoleRequestStatus.PENDING));
        if (licenses.latestStatusOf(userId).filter(s -> s == RealtorLicenseVerificationStatus.PENDING_REVIEW).isPresent())
            pending.add(ApplicationRoles.REALTOR);
        return List.copyOf(pending);
    }
}
