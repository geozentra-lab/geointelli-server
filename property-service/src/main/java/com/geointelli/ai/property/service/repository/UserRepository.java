package com.geointelli.ai.property.service.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.geointelli.ai.property.service.entity.UserPersona;

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

    Page<User> findByPersona(UserPersona persona, Pageable pageable);
    Optional<User> findByKeycloakUserId(String keycloakUserId);
    Optional<User> findByEmail(String email);
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
