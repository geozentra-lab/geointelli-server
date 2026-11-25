package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.RoleRequest;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;

/** Role requests of SCRUM-204 (table role_requests, V50). */
public interface RoleRequestRepository extends JpaRepository<RoleRequest, Long> {

    /** The requests of one user, newest first. */
    List<RoleRequest> findByUserIdOrderByIdDesc(Long userId);

    /** One request of its owner; another user's request is treated as missing. */
    Optional<RoleRequest> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndRequestedRoleAndStatus(Long userId, String requestedRole, RoleRequestStatus status);

    /** Roles the user is waiting for. */
    @Query("SELECT r.requestedRole FROM RoleRequest r WHERE r.userId = :userId AND r.status = :status")
    List<String> findRolesByUserIdAndStatus(@Param("userId") Long userId, @Param("status") RoleRequestStatus status);

    /** The newest approved request of a user for a role, marked REVOKED when an admin revokes the role. */
    Optional<RoleRequest> findFirstByUserIdAndRequestedRoleAndStatusOrderByIdDesc(Long userId, String requestedRole,
                                                                                  RoleRequestStatus status);

    Page<RoleRequest> findAllByOrderByIdDesc(Pageable pageable);

    Page<RoleRequest> findByStatusOrderByIdDesc(RoleRequestStatus status, Pageable pageable);

    Page<RoleRequest> findByRequestedRoleOrderByIdDesc(String requestedRole, Pageable pageable);

    Page<RoleRequest> findByStatusAndRequestedRoleOrderByIdDesc(RoleRequestStatus status, String requestedRole,
                                                                Pageable pageable);

    /** Loads a request for a decision; a concurrent decision on the same row waits and then sees it decided. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoleRequest r WHERE r.id = :id")
    Optional<RoleRequest> findByIdForUpdate(@Param("id") Long id);
}
