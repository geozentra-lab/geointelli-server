package com.geointelli.ai.property.service.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;
import jakarta.persistence.EntityManager;

/** All summary queries include both portfolio ID and owner ID, including internal component calls. */
@Repository
public class PortfolioSummaryRepository {
    private final EntityManager entityManager;
    public PortfolioSummaryRepository(EntityManager entityManager) { this.entityManager=entityManager; }

    public List<PortfolioHoldingSnapshot> holdings(Long portfolioId, Long userId) {
        return entityManager.createQuery("""
            SELECT new com.geointelli.ai.property.service.repository.PortfolioHoldingSnapshot(
              h.id,p.id,h.ownershipSharePercent,h.financingType,h.outstandingBalance,h.balanceAsOfDate,
              h.occupancyStatus,c.id,c.name,s.id,s.code,s.name,h.updatedAt)
            FROM PortfolioHolding h JOIN h.property p LEFT JOIN p.county c LEFT JOIN c.state s
            WHERE h.portfolio.id=:portfolioId AND h.portfolio.user.id=:userId ORDER BY h.id
            """,PortfolioHoldingSnapshot.class).setParameter("portfolioId",portfolioId)
            .setParameter("userId",userId).getResultList();
    }

    public long holdingCount(Long portfolioId, Long userId) {
        return entityManager.createQuery("SELECT COUNT(h) FROM PortfolioHolding h WHERE h.portfolio.id=:portfolioId "
            + "AND h.portfolio.user.id=:userId",Long.class).setParameter("portfolioId",portfolioId)
            .setParameter("userId",userId).getSingleResult();
    }

    /** Cash dates are inclusive; periodStart/periodEnd describe coverage and never prorate cash. */
    public List<PortfolioFinancialAggregate> financials(Long portfolioId, Long userId, LocalDate from, LocalDate to) {
        return entityManager.createQuery("""
            SELECT new com.geointelli.ai.property.service.repository.PortfolioFinancialAggregate(
              h.id,h.ownershipSharePercent,r.category,r.currency,SUM(r.amount),COUNT(r),MAX(r.updatedAt))
            FROM HoldingFinancialRecord r JOIN r.holding h
            WHERE h.portfolio.id=:portfolioId AND h.portfolio.user.id=:userId
              AND r.transactionDate>=:fromDate AND r.transactionDate<=:toDate
            GROUP BY h.id,h.ownershipSharePercent,r.category,r.currency
            ORDER BY h.id,r.category
            """,PortfolioFinancialAggregate.class).setParameter("portfolioId",portfolioId)
            .setParameter("userId",userId).setParameter("fromDate",from).setParameter("toDate",to).getResultList();
    }
}
