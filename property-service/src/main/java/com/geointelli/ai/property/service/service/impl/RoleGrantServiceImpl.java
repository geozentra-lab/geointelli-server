package com.geointelli.ai.property.service.service.impl;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.config.ApplicationRoles;
import com.geointelli.ai.property.service.dto.RealtorLicenseExpiryRunDTO;
import com.geointelli.ai.property.service.dto.RoleGrantEventDTO;
import com.geointelli.ai.property.service.entity.RoleGrantAction;
import com.geointelli.ai.property.service.entity.RoleGrantEvent;
import com.geointelli.ai.property.service.entity.RoleGrantSource;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.exception.KeycloakRoleException;
import com.geointelli.ai.property.service.repository.RoleGrantEventRepository;
import com.geointelli.ai.property.service.repository.RoleRequestRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.KeycloakRoleService;
import com.geointelli.ai.property.service.service.RoleGrantService;

/**
 * Keycloak first, history second (SCRUM-204). {@link KeycloakRoleException} does not mark the caller's transaction
 * for rollback: it is thrown before anything is written, so a caller may catch it and keep its own changes (the
 * investor self-service request stays PENDING). Keycloak mapping calls are idempotent, so retrying a decision whose
 * database write failed after Keycloak accepted it is safe.
 */
@Slf4j
@Service
public class RoleGrantServiceImpl implements RoleGrantService {

    private final KeycloakRoleService keycloak;
    private final RoleGrantEventRepository events;
    private final RoleRequestRepository requests;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final TransactionTemplate perUser;

    public RoleGrantServiceImpl(KeycloakRoleService keycloak, RoleGrantEventRepository events,
                                RoleRequestRepository requests, UserRepository users, CurrentUserService currentUser,
                                PlatformTransactionManager transactionManager) {
        this.keycloak = keycloak;
        this.events = events;
        this.requests = requests;
        this.users = users;
        this.currentUser = currentUser;
        this.perUser = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(noRollbackFor = KeycloakRoleException.class)
    public RoleGrantEvent grant(User user, String role, RoleGrantSource source, Long sourceId, String reason,
                                Long decidedByUserId) {
        keycloak.grantRealmRole(user.getKeycloakUserId(), role);
        return record(user, role, RoleGrantAction.GRANT, source, sourceId, reason, decidedByUserId);
    }

    @Override
    @Transactional(noRollbackFor = KeycloakRoleException.class)
    public RoleGrantEvent revoke(User user, String role, RoleGrantSource source, Long sourceId, String reason,
                                 Long decidedByUserId) {
        keycloak.revokeRealmRole(user.getKeycloakUserId(), role);
        return record(user, role, RoleGrantAction.REVOKE, source, sourceId, reason, decidedByUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleGrantEventDTO> history(Long userId) {
        requireUser(userId);
        return events.findByUserIdOrderByIdDesc(userId).stream().map(RoleGrantServiceImpl::toDTO).toList();
    }

    @Override
    @Transactional
    public RoleGrantEventDTO revokeByAdmin(Long userId, String role, String reason) {
        String normalized = role == null ? null : role.trim().toLowerCase(Locale.ROOT);
        if (normalized == null || !KeycloakRoleService.MANAGED_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("role must be investor, realtor or lender");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        Long adminId = currentUser.getCurrentUserEntity().getId();
        User user = requireUser(userId);
        // the role request that gave the role, if it came from one (realtor roles come from a license)
        var approved = requests.findFirstByUserIdAndRequestedRoleAndStatusOrderByIdDesc(
            userId, normalized, RoleRequestStatus.APPROVED);
        RoleGrantEvent event = revoke(user, normalized, RoleGrantSource.ADMIN_REVOCATION,
            approved.map(r -> r.getId()).orElse(null), reason.trim(), adminId);
        approved.ifPresent(request -> {
            request.setStatus(RoleRequestStatus.REVOKED);
            requests.save(request);
        });
        return toDTO(event);
    }

    @Override
    public RealtorLicenseExpiryRunDTO revokeExpiredRealtorGrants() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Long> candidates = events.findRealtorGrantsWithoutValidLicense(today);
        int revoked = 0;
        int failed = 0;
        for (Long userId : candidates) {
            try {
                perUser.executeWithoutResult(status -> {
                    User user = users.findById(userId).orElseThrow();
                    revoke(user, ApplicationRoles.REALTOR, RoleGrantSource.LICENSE_EXPIRY, null,
                        "No verified realtor license valid on " + today, null);
                });
                revoked++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Realtor role of user {} not revoked after license expiry: {}", userId,
                    e.getClass().getSimpleName());
            }
        }
        if (!candidates.isEmpty()) {
            log.info("Realtor license expiry on {}: {} candidates, {} revoked, {} failed", today, candidates.size(),
                revoked, failed);
        }
        return new RealtorLicenseExpiryRunDTO(today, candidates.size(), revoked, failed);
    }

    private RoleGrantEvent record(User user, String role, RoleGrantAction action, RoleGrantSource source,
                                  Long sourceId, String reason, Long decidedByUserId) {
        RoleGrantEvent event = new RoleGrantEvent();
        event.setUserId(user.getId());
        event.setRole(role);
        event.setAction(action);
        event.setSource(source);
        event.setSourceId(sourceId);
        event.setReason(reason);
        event.setDecidedByUserId(decidedByUserId);
        return events.save(event);
    }

    private User requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be a positive number");
        }
        return users.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + userId + " not found"));
    }

    static RoleGrantEventDTO toDTO(RoleGrantEvent event) {
        return new RoleGrantEventDTO(event.getId(), event.getUserId(), event.getRole(), event.getAction(),
            event.getSource(), event.getSourceId(), event.getReason(), event.getDecidedByUserId(),
            event.getCreatedAt());
    }
}
