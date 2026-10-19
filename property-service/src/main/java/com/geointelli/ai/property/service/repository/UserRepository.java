package com.geointelli.ai.property.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.geointelli.ai.property.service.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKeycloakUserId(String keycloakUserId);
    Optional<User> findByEmail(String email);
    boolean existsByKeycloakUserId(String keycloakUserId);
}
