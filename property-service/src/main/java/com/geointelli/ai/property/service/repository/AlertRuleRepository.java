package com.geointelli.ai.property.service.repository;

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

    /** Locks only the rule; avoids locking the nullable side of a saved-search outer join. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AlertRule r WHERE r.id = :id AND r.user.id = :userId")
    Optional<AlertRule> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByUser_IdAndProperty_IdAndEventType(Long userId, Long propertyId, AlertEventType eventType);
    boolean existsByUser_IdAndSavedSearch_IdAndEventType(Long userId, Long savedSearchId, AlertEventType eventType);
}
