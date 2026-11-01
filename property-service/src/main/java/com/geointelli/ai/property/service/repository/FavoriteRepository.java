package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.geointelli.ai.property.service.entity.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    boolean existsByUserIdAndPropertyId(Long userId, Long propertyId);
    Optional<Favorite> findByUserIdAndPropertyId(Long userId, Long propertyId);
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByUserIdAndPropertyId(Long userId, Long propertyId);

    /** Number of favorites owned by the user. */
    long countByUserId(Long userId);

    /** The newest favorites of the user, bounded by the page size (for example {@code PageRequest.of(0, 5)}). */
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
