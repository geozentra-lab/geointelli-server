package com.geointelli.ai.property.service.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.Portfolio;
import jakarta.persistence.LockModeType;

/** Every lookup is scoped to the authenticated user's internal ID. */
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    Page<Portfolio> findByUser_Id(Long userId, Pageable pageable);
    Optional<Portfolio> findByIdAndUser_Id(Long id, Long userId);

    /** Serializes holding changes and portfolio deletion on the same owned portfolio. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Portfolio p WHERE p.id = :id AND p.user.id = :userId")
    Optional<Portfolio> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
