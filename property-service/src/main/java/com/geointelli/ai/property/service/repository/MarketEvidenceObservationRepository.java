package com.geointelli.ai.property.service.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.MarketEvidenceObservation;
import com.geointelli.ai.property.service.entity.MarketEvidenceType;
import com.geointelli.ai.property.service.entity.MarketGeographyLevel;

/** Market evidence rows (SCRUM-131): the idempotent lookup used by imports and the paged read by market. */
public interface MarketEvidenceObservationRepository extends JpaRepository<MarketEvidenceObservation, Long> {

    /**
     * The row that a repeated import must update instead of duplicating: same source, evidence type, geography
     * and period. Matches the table's unique constraint exactly.
     */
    @Query("SELECT o FROM MarketEvidenceObservation o WHERE o.source = :source AND o.evidenceType = :type "
        + "AND o.geographyLevel = :level AND o.geographyCode = :code "
        + "AND o.periodStart = :periodStart AND o.periodEnd = :periodEnd")
    Optional<MarketEvidenceObservation> findObservation(@Param("source") String source,
                                                        @Param("type") MarketEvidenceType type,
                                                        @Param("level") MarketGeographyLevel level,
                                                        @Param("code") String code,
                                                        @Param("periodStart") LocalDate periodStart,
                                                        @Param("periodEnd") LocalDate periodEnd);

    /**
     * Observations of one market, newest period first. Geography is required; evidence type, source and the
     * period window are optional filters (a null parameter means "any"). A period matches when it overlaps the
     * window: it ends on or after {@code from} and starts on or before {@code to}. The order ends on the ID, so
     * pages never repeat or skip a row when several periods start on the same day.
     */
    @Query("SELECT o FROM MarketEvidenceObservation o WHERE o.geographyLevel = :level AND o.geographyCode = :code "
        + "AND (:type IS NULL OR o.evidenceType = :type) "
        + "AND (:source IS NULL OR o.source = :source) "
        + "AND (CAST(:from AS date) IS NULL OR o.periodEnd >= :from) "
        + "AND (CAST(:to AS date) IS NULL OR o.periodStart <= :to) "
        + "ORDER BY o.periodStart DESC, o.evidenceType ASC, o.source ASC, o.periodEnd DESC, o.id ASC")
    Page<MarketEvidenceObservation> findMarket(@Param("level") MarketGeographyLevel level,
                                               @Param("code") String code,
                                               @Param("type") MarketEvidenceType type,
                                               @Param("source") String source,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to,
                                               Pageable pageable);
}
