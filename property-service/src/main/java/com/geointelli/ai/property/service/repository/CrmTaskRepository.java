package com.geointelli.ai.property.service.repository;

import java.time.Instant;
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
