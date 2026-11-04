package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.CrmTransaction;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;
import jakarta.persistence.LockModeType;

/** Owned deal reads and stage counts; transitions lock only the affected deal. */
public interface CrmTransactionRepository extends JpaRepository<CrmTransaction, Long> {
    Page<CrmTransaction> findByUserId(Long userId, Pageable pageable);
    Page<CrmTransaction> findByUserIdAndStage(Long userId, CrmTransactionStage stage, Pageable pageable);
    Optional<CrmTransaction> findByIdAndUserId(Long id, Long userId);
    boolean existsByClientId(Long clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM CrmTransaction t WHERE t.id = :id AND t.userId = :owner")
    Optional<CrmTransaction> findOwnedForUpdate(@Param("id") Long id, @Param("owner") Long owner);

    @Query("SELECT t.stage AS stage, COUNT(t) AS count FROM CrmTransaction t WHERE t.userId = :owner GROUP BY t.stage")
    List<CrmPipelineCount> pipeline(@Param("owner") Long owner);
}
