package com.geointelli.ai.property.service.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.CrmTask;
import com.geointelli.ai.property.service.entity.CrmTaskStatus;
import jakarta.persistence.LockModeType;

/** Task access and aggregates always include the authenticated owner. */
public interface CrmTaskRepository extends JpaRepository<CrmTask, Long> {
    Page<CrmTask> findByUserId(Long userId, Pageable pageable);
    Page<CrmTask> findByUserIdAndStatus(Long userId, CrmTaskStatus status, Pageable pageable);
    Optional<CrmTask> findByIdAndUserId(Long id, Long userId);
    boolean existsByClientId(Long clientId);

    /** Active (OPEN/IN_PROGRESS) tasks of this user already past their due date, earliest first; bounded by the pageable. */
    List<CrmTask> findByUserIdAndStatusInAndDueAtBeforeOrderByDueAtAsc(Long userId, Collection<CrmTaskStatus> statuses,
        Instant before, Pageable pageable);

    /** Active tasks of this user due inside [from, to], earliest first; bounded by the pageable. */
    List<CrmTask> findByUserIdAndStatusInAndDueAtBetweenOrderByDueAtAsc(Long userId, Collection<CrmTaskStatus> statuses,
        Instant from, Instant to, Pageable pageable);

    /** How many active tasks of this user are due inside [from, to], regardless of the list bound. */
    long countByUserIdAndStatusInAndDueAtBetween(Long userId, Collection<CrmTaskStatus> statuses, Instant from, Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM CrmTask t WHERE t.id = :id AND t.userId = :owner")
    Optional<CrmTask> findOwnedForUpdate(@Param("id") Long id, @Param("owner") Long owner);

    @Query("""
        SELECT COUNT(t) AS total,
          COALESCE(SUM(CASE WHEN t.status = :open THEN 1 ELSE 0 END),0) AS open,
          COALESCE(SUM(CASE WHEN t.status = :progress THEN 1 ELSE 0 END),0) AS inProgress,
          COALESCE(SUM(CASE WHEN t.status = :completed THEN 1 ELSE 0 END),0) AS completed,
          COALESCE(SUM(CASE WHEN t.status = :cancelled THEN 1 ELSE 0 END),0) AS cancelled,
          COALESCE(SUM(CASE WHEN t.status IN (:open,:progress) AND t.dueAt < :now THEN 1 ELSE 0 END),0) AS overdue
        FROM CrmTask t WHERE t.userId = :owner
        """)
    CrmTaskSummaryCount summary(@Param("owner") Long owner, @Param("now") Instant now,
        @Param("open") CrmTaskStatus open, @Param("progress") CrmTaskStatus progress,
        @Param("completed") CrmTaskStatus completed, @Param("cancelled") CrmTaskStatus cancelled);
}
