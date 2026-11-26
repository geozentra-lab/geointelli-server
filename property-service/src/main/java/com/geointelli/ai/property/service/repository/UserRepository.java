package com.geointelli.ai.property.service.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    /** Orders multi-user contact submissions without blocking ordinary foreign-key checks. */
    @Query(value = "SELECT * FROM users WHERE id = :id FOR NO KEY UPDATE", nativeQuery = true)
    Optional<User> lockForContact(@Param("id") Long id);

    /**
     * Condition of an active realtor (SCRUM-204), on users alias u: a VERIFIED license valid on :today (no expiration
     * date, or expiring today or later), and GeoZentra's latest realtor event, if any, is not a revocation (the
     * newest REVOKE is not after the newest GRANT; no event at all counts as not revoked).
     */
    String ACTIVE_REALTOR = """
        btrim(u.keycloak_user_id) <> ''
        AND EXISTS (SELECT 1 FROM realtor_license_verifications v
                    WHERE v.user_id = u.id AND v.status = 'VERIFIED'
                      AND (v.license_expiration_date IS NULL OR v.license_expiration_date >= :today))
        AND COALESCE((SELECT MAX(e.id) FROM role_grant_events e
                      WHERE e.user_id = u.id AND e.role = 'realtor' AND e.action = 'REVOKE'), 0)
            <= COALESCE((SELECT MAX(g.id) FROM role_grant_events g
                         WHERE g.user_id = u.id AND g.role = 'realtor' AND g.action = 'GRANT'), 0)
        """;

    /**
     * Realtors a user can contact, newest first: the active realtors (see {@link #ACTIVE_REALTOR}); the persona is not
     * read. Pass an unsorted page: the order is part of the query.
     */
    @Query(value = "SELECT u.* FROM users u WHERE " + ACTIVE_REALTOR + " ORDER BY u.id DESC",
        countQuery = "SELECT COUNT(*) FROM users u WHERE " + ACTIVE_REALTOR, nativeQuery = true)
    Page<User> findActiveRealtors(@Param("today") java.time.LocalDate today, Pageable pageable);

    /** Whether the user is an active realtor (see {@link #ACTIVE_REALTOR}). */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM users u WHERE u.id = :userId AND " + ACTIVE_REALTOR + ")",
        nativeQuery = true)
    boolean isActiveRealtor(@Param("userId") Long userId, @Param("today") java.time.LocalDate today);
    Optional<User> findByKeycloakUserId(String keycloakUserId);
    /**
     * The account that uses this email, ignoring case. Emails are not unique here: deleting a Keycloak account and
     * signing up again with the same email leaves the old row behind, so the newest row (the live account) wins.
     */
    Optional<User> findFirstByEmailIgnoreCaseOrderByIdDesc(String email);
    boolean existsByKeycloakUserId(String keycloakUserId);

    /**
     * Provisions a first-login user without failing when concurrent requests race to create the
     * same identity. Runs in its own transaction so callers inside read-only transactions can
     * still provision, and the row is committed before the caller reads it back.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
        INSERT INTO users (keycloak_user_id, username, email, first_name, last_name, persona, created_at, updated_at)
        VALUES (:keycloakUserId, :username, :email, :firstName, :lastName, 'USER', NOW(), NOW())
        ON CONFLICT (keycloak_user_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("keycloakUserId") String keycloakUserId,
        @Param("username") String username,
        @Param("email") String email,
        @Param("firstName") String firstName,
        @Param("lastName") String lastName);
}
