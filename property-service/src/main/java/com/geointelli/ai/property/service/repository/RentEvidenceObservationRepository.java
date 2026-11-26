package com.geointelli.ai.property.service.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.RentEvidenceObservation;
import com.geointelli.ai.property.service.entity.RentEvidenceType;
import com.geointelli.ai.property.service.entity.RentGeographyLevel;
import com.geointelli.ai.property.service.entity.RentPeriod;

/** Rent evidence rows (SCRUM-132): the idempotent lookup used by imports and the paged read by geography. */
public interface RentEvidenceObservationRepository extends JpaRepository<RentEvidenceObservation, Long> {

    /**
     * The row that a repeated import must update instead of duplicating: same source, evidence type, geography,
     * period, rent period and bedrooms. Matches the table's unique constraint; a null {@code bedrooms} matches
     * only a row whose bedrooms are also null (all sizes together).
     */
    @Query("SELECT o FROM RentEvidenceObservation o WHERE o.source = :source AND o.evidenceType = :type "
        + "AND o.geographyLevel = :level AND o.geographyCode = :code "
        + "AND o.periodStart = :periodStart AND o.periodEnd = :periodEnd AND o.rentPeriod = :rentPeriod "
        + "AND ((:bedrooms IS NULL AND o.bedrooms IS NULL) OR o.bedrooms = :bedrooms)")
    Optional<RentEvidenceObservation> findObservation(@Param("source") String source,
                                                      @Param("type") RentEvidenceType type,
                                                      @Param("level") RentGeographyLevel level,
                                                      @Param("code") String code,
                                                      @Param("periodStart") LocalDate periodStart,
                                                      @Param("periodEnd") LocalDate periodEnd,
                                                      @Param("rentPeriod") RentPeriod rentPeriod,
                                                      @Param("bedrooms") Short bedrooms);

    /**
     * Observations of one geography, newest period first. Geography is required; evidence type, source, rent
     * period and the period window are optional filters (a null parameter means "any"). A period matches when it
     * overlaps the window: it ends on or after {@code from} and starts on or before {@code to}.
     */
    @Query("SELECT o FROM RentEvidenceObservation o WHERE o.geographyLevel = :level AND o.geographyCode = :code "
        + "AND (:type IS NULL OR o.evidenceType = :type) "
        + "AND (:source IS NULL OR o.source = :source) "
        + "AND (:rentPeriod IS NULL OR o.rentPeriod = :rentPeriod) "
        + "AND (CAST(:from AS date) IS NULL OR o.periodEnd >= :from) "
        + "AND (CAST(:to AS date) IS NULL OR o.periodStart <= :to) "
        + "ORDER BY o.periodStart DESC, o.evidenceType ASC, o.source ASC, o.bedrooms ASC NULLS FIRST")
    Page<RentEvidenceObservation> findGeography(@Param("level") RentGeographyLevel level,
                                                @Param("code") String code,
                                                @Param("type") RentEvidenceType type,
                                                @Param("source") String source,
                                                @Param("rentPeriod") RentPeriod rentPeriod,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to,
                                                Pageable pageable);
}
