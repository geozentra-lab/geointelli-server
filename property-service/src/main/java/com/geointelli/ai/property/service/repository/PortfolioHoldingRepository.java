package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.PortfolioHolding;

/** Loads holdings through an already ownership-checked portfolio. */
public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {
    @EntityGraph(attributePaths = {"property", "property.county"})
    Page<PortfolioHolding> findByPortfolio_Id(Long portfolioId, Pageable pageable);

    @EntityGraph(attributePaths = {"property", "property.county"})
    Optional<PortfolioHolding> findByIdAndPortfolio_Id(Long id, Long portfolioId);

    boolean existsByPortfolio_IdAndProperty_Id(Long portfolioId, Long propertyId);
    long countByPortfolio_Id(Long portfolioId);

    @Query("SELECT h.portfolio.id AS portfolioId, COUNT(h) AS holdingCount FROM PortfolioHolding h "
        + "WHERE h.portfolio.id IN :ids GROUP BY h.portfolio.id")
    List<PortfolioHoldingCount> countForPortfolios(@Param("ids") List<Long> ids);

    @Modifying
    @Query("DELETE FROM PortfolioHolding h WHERE h.portfolio.id = :portfolioId")
    void deleteForPortfolio(@Param("portfolioId") Long portfolioId);
}
