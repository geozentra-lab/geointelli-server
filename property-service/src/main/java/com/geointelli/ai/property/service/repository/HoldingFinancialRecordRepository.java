package com.geointelli.ai.property.service.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import com.geointelli.ai.property.service.entity.HoldingFinancialRecord;

/** Financial records are always fetched through an already ownership-checked holding. */
public interface HoldingFinancialRecordRepository extends JpaRepository<HoldingFinancialRecord, Long> {
    Optional<HoldingFinancialRecord> findByIdAndHolding_Id(Long id, Long holdingId);

    @Query("SELECT r FROM HoldingFinancialRecord r WHERE r.holding.id = :holdingId "
        + "AND r.transactionDate >= COALESCE(:fromDate, r.transactionDate) "
        + "AND r.transactionDate <= COALESCE(:toDate, r.transactionDate) "
        + "AND r.category = COALESCE(:category, r.category)")
    Page<HoldingFinancialRecord> findRecords(@Param("holdingId") Long holdingId,
        @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
        @Param("category") FinancialRecordCategory category, Pageable pageable);
}
