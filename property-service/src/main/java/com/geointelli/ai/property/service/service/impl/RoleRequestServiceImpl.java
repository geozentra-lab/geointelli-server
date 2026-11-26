package com.geointelli.ai.property.service.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.config.ApplicationRoles;
import com.geointelli.ai.property.service.config.OnboardingProperties;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.RoleRequestCreateRequest;
import com.geointelli.ai.property.service.dto.RoleRequestDTO;
import com.geointelli.ai.property.service.dto.RoleRequestReviewDTO;
import com.geointelli.ai.property.service.entity.RoleGrantSource;
import com.geointelli.ai.property.service.entity.RoleRequest;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.exception.KeycloakRoleException;
import com.geointelli.ai.property.service.repository.RoleRequestRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.OnboardingService;
import com.geointelli.ai.property.service.service.RoleGrantService;
import com.geointelli.ai.property.service.service.RoleRequestService;

/**
 * Role requests of SCRUM-204. Decisions lock the request row, so two admins deciding at the same time, or an admin
 * and the applicant cancelling, are serialised and the second one gets 409. Approval calls Keycloak before writing
 * anything: if Keycloak fails the request stays PENDING.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RoleRequestServiceImpl implements RoleRequestService {

    static final Set<String> REQUESTABLE_ROLES = Set.of(ApplicationRoles.INVESTOR, ApplicationRoles.LENDER);
    static final String SELF_SERVICE_REASON = "Granted on request: self-service role";
    private static final int MAX_PAGE_SIZE = 100;

    private final RoleRequestRepository requests;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final RoleGrantService roleGrants;
    private final OnboardingProperties onboarding;
    private final OnboardingService accountRoles;

    @Override
    public RoleRequestDTO create(RoleRequestCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("role is required");
        }
        String role = normalizeRole(request.role());
        if (ApplicationRoles.REALTOR.equals(role)) {
            throw new IllegalArgumentException(
                "Realtor access is requested by submitting a license: POST /api/users/me/realtor-license");
        }
        if (!REQUESTABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("role must be investor or lender");
        }
        String organization = trimToNull(request.organizationName());
        User user = currentUser.getCurrentUserEntity();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Set<String> held = accountRoles.heldProfessionalRoles(user, authentication);
        if (held.contains(role)
                || (authentication != null && ApplicationRoles.hasRole(authentication, ApplicationRoles.ADMIN))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have access to the " + role
                + " features; if the role was just granted, refresh your access token");
        }
        if (requests.existsByUserIdAndRequestedRoleAndStatus(user.getId(), role, RoleRequestStatus.PENDING)) {
            throw pendingConflict(role);
        }
        // one professional role per account: another held or pending role refuses this one (409)
        accountRoles.requireNoOtherProfessionalRole(user, role, held);

        RoleRequest row = new RoleRequest();
        row.setUserId(user.getId());
        row.setRequestedRole(role);
        row.setOrganizationName(organization);
        row.setNote(trimToNull(request.note()));
        try {
            row = requests.saveAndFlush(row);
        } catch (DataIntegrityViolationException e) {
            // the partial unique index ux_role_requests_one_pending caught a concurrent request
            throw pendingConflict(role);
        }
        // choosing investor or lender answers the onboarding page, whatever the decision
        accountRoles.markCompleted(user);

        if (onboarding.isSelfService(role)) {
            try {
                roleGrants.grant(user, role, RoleGrantSource.SELF_SERVICE, row.getId(), SELF_SERVICE_REASON, null);
                row.setStatus(RoleRequestStatus.APPROVED);
                row.setDecisionReason(SELF_SERVICE_REASON);
                row.setDecidedAt(now());
                row = requests.saveAndFlush(row);
            } catch (KeycloakRoleException e) {
                // the request stays PENDING: an admin can approve it once Keycloak is back
                log.warn("Self-service {} grant failed for request {}; left PENDING: {}", role, row.getId(),
                    e.getReason());
            }
        }
        return toDTO(row);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleRequestDTO> listMine() {
        Long userId = currentUser.getCurrentUserEntity().getId();
        return requests.findByUserIdOrderByIdDesc(userId).stream().map(RoleRequestServiceImpl::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoleRequestDTO getMine(Long id) {
        Long userId = currentUser.getCurrentUserEntity().getId();
        return toDTO(requests.findByIdAndUserId(requirePositive(id), userId).orElseThrow(() -> notFound(id)));
    }

    @Override
    public RoleRequestDTO cancelMine(Long id) {
        Long userId = currentUser.getCurrentUserEntity().getId();
        RoleRequest row = requests.findByIdForUpdate(requirePositive(id))
            .filter(r -> r.getUserId().equals(userId))
            .orElseThrow(() -> notFound(id));
        requirePending(row);
        row.setStatus(RoleRequestStatus.CANCELLED);
        row.setDecidedByUserId(userId);
        row.setDecidedAt(now());
        return toDTO(requests.saveAndFlush(row));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleRequestReviewDTO> list(RoleRequestStatus status, String role, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String roleFilter = null;
        if (role != null && !role.isBlank()) {
            roleFilter = normalizeRole(role);
            if (!REQUESTABLE_ROLES.contains(roleFilter)) {
                throw new IllegalArgumentException("role must be investor or lender");
            }
        }
        PageRequest pageable = PageRequest.of(page, size);
        Page<RoleRequest> rows;
        if (status != null && roleFilter != null) {
            rows = requests.findByStatusAndRequestedRoleOrderByIdDesc(status, roleFilter, pageable);
        } else if (status != null) {
            rows = requests.findByStatusOrderByIdDesc(status, pageable);
        } else if (roleFilter != null) {
            rows = requests.findByRequestedRoleOrderByIdDesc(roleFilter, pageable);
        } else {
            rows = requests.findAllByOrderByIdDesc(pageable);
        }
        // one query for the applicant e-mails of the whole page instead of one per row
        Map<Long, String> emails = users.findAllById(
                rows.getContent().stream().map(RoleRequest::getUserId).collect(Collectors.toSet()))
            .stream().filter(u -> u.getEmail() != null)
            .collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
        return PageResponse.from(rows.map(row -> toReviewDTO(row, emails.get(row.getUserId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public RoleRequestReviewDTO get(Long id) {
        RoleRequest row = requests.findById(requirePositive(id)).orElseThrow(() -> notFound(id));
        return toReviewDTO(row, applicantEmail(row));
    }

    @Override
    public RoleRequestReviewDTO approve(Long id, String reason) {
        Long adminId = currentUser.getCurrentUserEntity().getId();
        RoleRequest row = requests.findByIdForUpdate(requirePositive(id)).orElseThrow(() -> notFound(id));
        requirePending(row);
        User applicant = users.findById(row.getUserId())
            .orElseThrow(() -> new IllegalStateException("Applicant " + row.getUserId() + " of request " + id
                + " does not exist"));
        String decisionReason = trimToNull(reason);
        // Keycloak first: if it fails nothing below runs and the request stays PENDING
        roleGrants.grant(applicant, row.getRequestedRole(), RoleGrantSource.ROLE_REQUEST, row.getId(),
            decisionReason != null ? decisionReason : "Role request " + row.getId() + " approved", adminId);
        row.setStatus(RoleRequestStatus.APPROVED);
        row.setDecisionReason(decisionReason);
        row.setDecidedByUserId(adminId);
        row.setDecidedAt(now());
        RoleRequest saved = requests.saveAndFlush(row);
        return toReviewDTO(saved, applicant.getEmail());
    }

    @Override
    public RoleRequestReviewDTO reject(Long id, String reason) {
        String decisionReason = trimToNull(reason);
        if (decisionReason == null) {
            throw new IllegalArgumentException("reason is required when rejecting; the applicant will read it");
        }
        Long adminId = currentUser.getCurrentUserEntity().getId();
        RoleRequest row = requests.findByIdForUpdate(requirePositive(id)).orElseThrow(() -> notFound(id));
        requirePending(row);
        row.setStatus(RoleRequestStatus.REJECTED);
        row.setDecisionReason(decisionReason);
        row.setDecidedByUserId(adminId);
        row.setDecidedAt(now());
        RoleRequest saved = requests.saveAndFlush(row);
        return toReviewDTO(saved, applicantEmail(saved));
    }

    private static void requirePending(RoleRequest row) {
        if (row.getStatus() != RoleRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role request " + row.getId()
                + " was already decided (" + row.getStatus() + ")");
        }
    }

    private static ResponseStatusException pendingConflict(String role) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "A " + role + " request is already pending");
    }

    private static ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Role request " + id + " not found");
    }

    private static Long requirePositive(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id must be a positive number");
        }
        return id;
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant now() {
        // PostgreSQL keeps microseconds; truncating makes the response equal to what a later GET returns
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private String applicantEmail(RoleRequest row) {
        return users.findById(row.getUserId()).map(User::getEmail).orElse(null);
    }

    static RoleRequestDTO toDTO(RoleRequest row) {
        return new RoleRequestDTO(row.getId(), row.getRequestedRole(), row.getStatus(), row.getOrganizationName(),
            row.getNote(), row.getDecisionReason(), row.getCreatedAt(), row.getDecidedAt());
    }

    private static RoleRequestReviewDTO toReviewDTO(RoleRequest row, String applicantEmail) {
        return new RoleRequestReviewDTO(row.getId(), row.getUserId(), applicantEmail, row.getRequestedRole(),
            row.getStatus(), row.getOrganizationName(), row.getNote(), row.getDecisionReason(),
            row.getDecidedByUserId(), row.getCreatedAt(), row.getDecidedAt());
    }
}
