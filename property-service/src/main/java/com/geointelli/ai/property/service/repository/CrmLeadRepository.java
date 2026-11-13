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
import com.geointelli.ai.property.service.entity.CrmLead;
import com.geointelli.ai.property.service.entity.LeadStage;
import jakarta.persistence.LockModeType;

/** Owner scoping and database-enforced idempotency apply independently of persona. */
public interface CrmLeadRepository extends JpaRepository<CrmLead, Long> {
    Page<CrmLead> findByUser_Id(Long userId, Pageable pageable);
    Page<CrmLead> findByUser_IdAndStage(Long userId, LeadStage stage, Pageable pageable);
    Optional<CrmLead> findByIdAndUser_Id(Long id, Long userId);
    Optional<CrmLead> findByUser_IdAndRequestKey(Long userId, String requestKey);
    boolean existsByClient_Id(Long clientId);

    /** One row per stage that has at least one lead of this user; absent stages mean zero. */
    @Query("SELECT l.stage AS stage, COUNT(l) AS count FROM CrmLead l WHERE l.user.id = :userId GROUP BY l.stage")
    List<LeadStageCount> countByStage(@Param("userId") Long userId);

    /** Most recently created leads of this user with their client loaded; the pageable bounds the result. */
    @EntityGraph(attributePaths = "client")
    List<CrmLead> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Projection for {@link #countByStage(Long)}: a stage and how many leads are in it. */
    interface LeadStageCount {
        LeadStage getStage();
        long getCount();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM CrmLead l WHERE l.id = :id AND l.user.id = :userId")
    Optional<CrmLead> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
