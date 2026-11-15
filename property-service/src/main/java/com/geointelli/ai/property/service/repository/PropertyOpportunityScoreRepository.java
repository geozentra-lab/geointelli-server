package com.geointelli.ai.property.service.repository;

import com.geointelli.ai.property.service.entity.PropertyOpportunityScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/** Reads the stored opportunity scores for browsing (SCRUM-168); the refresh job writes them. */
public interface PropertyOpportunityScoreRepository extends JpaRepository<PropertyOpportunityScore, Long> {

    /**
     * One page of the SCORED rows of a scoring version; every filter is optional (null = not applied).
     * The order comes from the pageable.
     */
    @Query("""
            SELECT s FROM PropertyOpportunityScore s
            WHERE s.scoreVersion = :scoreVersion AND s.status = 'SCORED'
              AND (:countyId IS NULL OR s.countyId = :countyId)
              AND (:stateId IS NULL OR s.stateId = :stateId)
              AND (:minScore IS NULL OR s.score >= :minScore)
              AND (:minValue IS NULL OR s.estimatedValue >= :minValue)
              AND (:maxValue IS NULL OR s.estimatedValue <= :maxValue)
            """)
    Page<PropertyOpportunityScore> browse(@Param("scoreVersion") String scoreVersion, @Param("countyId") Long countyId,
                                          @Param("stateId") Long stateId, @Param("minScore") BigDecimal minScore,
                                          @Param("minValue") BigDecimal minValue, @Param("maxValue") BigDecimal maxValue,
                                          Pageable pageable);

    /** Whether any score of the version is stored (SCORED or UNAVAILABLE). */
    boolean existsByScoreVersion(String scoreVersion);

    /** Whether any score of the version with the given status is stored. */
    boolean existsByScoreVersionAndStatus(String scoreVersion, String status);

    /** Display details of the listed properties: folio, address, county and state. */
    @Query("""
            SELECT p.id AS propertyId, p.folio AS folio, a.address AS address, a.city AS city, a.zip AS zip,
                   c.name AS countyName, st.code AS stateCode
            FROM Property p LEFT JOIN p.address a LEFT JOIN p.county c LEFT JOIN c.state st
            WHERE p.id IN :ids
            """)
    List<PropertyRow> findPropertyRows(@Param("ids") Collection<Long> ids);

    /** Projection for {@link #findPropertyRows(Collection)}. */
    interface PropertyRow {
        Long getPropertyId();

        String getFolio();

        String getAddress();

        String getCity();

        String getZip();

        String getCountyName();

        String getStateCode();
    }
}
