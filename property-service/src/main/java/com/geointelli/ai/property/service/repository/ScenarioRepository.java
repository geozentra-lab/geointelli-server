package com.geointelli.ai.property.service.repository;

import com.geointelli.ai.property.service.entity.Scenario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Owner-scoped lookups: a user can never read, change or delete another user's scenario. */
public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    /** One page of the scenarios owned by the user; the order comes from the pageable. */
    Page<Scenario> findByUser_Id(Long userId, Pageable pageable);

    /** The scenario only if it belongs to the user; empty for a missing or foreign scenario. */
    Optional<Scenario> findByIdAndUser_Id(Long id, Long userId);
}
