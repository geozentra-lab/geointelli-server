package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.SavedSearch;
import jakarta.persistence.LockModeType;

/** Owner-scoped lookups avoid exposing other users' saved search definitions. */
public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {
    Page<SavedSearch> findByUser_Id(Long userId, Pageable pageable);
    Optional<SavedSearch> findByIdAndUser_Id(Long id, Long userId);

    /** Number of saved searches owned by the user (dashboard card); nothing is executed. */
    long countByUser_Id(Long userId);
    /** The newest saved searches of the user, bounded by the page size (for example {@code PageRequest.of(0, 5)}). */
    List<SavedSearch> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SavedSearch s WHERE s.id = :id AND s.user.id = :userId")
    Optional<SavedSearch> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
