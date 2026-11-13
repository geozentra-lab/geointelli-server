package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.CrmClient;
import jakarta.persistence.LockModeType;

/** Every accessible contact is looked up under its owner. */
public interface CrmClientRepository extends JpaRepository<CrmClient, Long> {
    Page<CrmClient> findByUser_Id(Long userId, Pageable pageable);
    Optional<CrmClient> findByIdAndUser_Id(Long id, Long userId);
    Optional<CrmClient> findByUser_IdAndEmail(Long userId, String email);

    /** Number of contacts owned by this user; used by the realtor dashboard card. */
    long countByUser_Id(Long userId);

    /** Most recently created contacts of this user; the pageable bounds the result (e.g. first 5). */
    List<CrmClient> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Serializes edits, deletion and lead submission for this contact only. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CrmClient c WHERE c.id = :id AND c.user.id = :userId")
    Optional<CrmClient> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
