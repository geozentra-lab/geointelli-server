package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.AlertEventType;
import com.geointelli.ai.property.service.entity.AlertRule;
import jakarta.persistence.LockModeType;

/** Every public-facing lookup is scoped to the authenticated owner's internal ID. */
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    @EntityGraph(attributePaths = "savedSearch")
    Page<AlertRule> findByUser_Id(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "savedSearch")
    Optional<AlertRule> findByIdAndUser_Id(Long id, Long userId);

    /** Number of alert rules owned by the user (dashboard card); delivered notifications are never counted. */
    long countByUser_Id(Long userId);

    /** Rules of the user that are effectively paused: paused themselves or linked to a paused saved search. */
    @Query("SELECT COUNT(r) FROM AlertRule r LEFT JOIN r.savedSearch s WHERE r.user.id = :userId AND (r.paused = true OR s.paused = true)")
    long countEffectivelyPausedByUserId(@Param("userId") Long userId);

    /** The newest rules of the user, bounded by the page size; the saved search is loaded for the effective pause state. */
    @EntityGraph(attributePaths = "savedSearch")
    List<AlertRule> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Locks only the rule; avoids locking the nullable side of a saved-search outer join. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AlertRule r WHERE r.id = :id AND r.user.id = :userId")
    Optional<AlertRule> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByUser_IdAndProperty_IdAndEventType(Long userId, Long propertyId, AlertEventType eventType);
    boolean existsByUser_IdAndSavedSearch_IdAndEventType(Long userId, Long savedSearchId, AlertEventType eventType);
}
