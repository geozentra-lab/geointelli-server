package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.geointelli.ai.property.service.entity.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    boolean existsByUserIdAndPropertyId(Long userId, Long propertyId);
    Optional<Favorite> findByUserIdAndPropertyId(Long userId, Long propertyId);
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByUserIdAndPropertyId(Long userId, Long propertyId);
}
