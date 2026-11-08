package com.geointelli.ai.property.service.repository;

import com.geointelli.ai.property.service.entity.Property;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read-only SQL checks of the data-quality rules (SCRUM-189). Each rule is a constant subquery returning
 * {@code entity_id}, {@code property_id} and {@code detail}; it is reused by a count query (summary) and a
 * paged query (issues, ordered by entity ID so pages are stable). Nothing is updated or deleted.
 */
public interface DataQualityRepository extends Repository<Property, Long> {

    /** Property without a row in addresses. */
    String MISSING_ADDRESS = """
            SELECT p.id AS entity_id, p.id AS property_id, 'No row in addresses' AS detail
            FROM properties p
            WHERE NOT EXISTS (SELECT 1 FROM addresses a WHERE a.property_id = p.id)
            """;

    /** Property without a parcel, neither its own nor its parent property's (condo units use the parent parcel). */
    String MISSING_PARCEL = """
            SELECT p.id AS entity_id, p.id AS property_id,
                'No parcel for the property or its parent property' AS detail
            FROM properties p
            WHERE NOT EXISTS (SELECT 1 FROM parcels pa WHERE pa.property_id = p.id)
              AND (p.parent_property_id IS NULL
                   OR NOT EXISTS (SELECT 1 FROM parcels pa WHERE pa.property_id = p.parent_property_id))
            """;

    /** Latest assessment year older than the current year minus 2. */
    String STALE_ASSESSMENT = """
            SELECT a.property_id AS entity_id, a.property_id AS property_id,
                'Latest assessment year ' || MAX(a.year) AS detail
            FROM assessments a
            WHERE a.property_id IS NOT NULL AND a.year IS NOT NULL
            GROUP BY a.property_id
            HAVING MAX(a.year) < EXTRACT(YEAR FROM CURRENT_DATE) - 2
            """;

    /** Property and parcel lot sizes differ by more than 10% of the larger one. */
    String LOT_SIZE_CONFLICT = """
            SELECT p.id AS entity_id, p.id AS property_id,
                'Property lot size ' || p.lot_size || ' vs parcel lot size ' || pa.lot_size AS detail
            FROM properties p
            JOIN LATERAL (
                SELECT pa.lot_size FROM parcels pa
                WHERE pa.property_id = p.id AND pa.lot_size > 0
                ORDER BY pa.fid
                LIMIT 1
            ) pa ON true
            WHERE p.lot_size > 0
              AND ABS(p.lot_size - pa.lot_size) > 0.10 * GREATEST(p.lot_size, pa.lot_size)
            """;

    /** Qualified sale with a price of 0 or less. */
    String SALE_PRICE_OUTLIER = """
            SELECT s.id AS entity_id, s.property_id AS property_id,
                'Qualified sale with price ' || s.sale_price AS detail
            FROM sales s
            WHERE s.property_id IS NOT NULL
              AND UPPER(BTRIM(s.qualified_flag)) = 'Q'
              AND s.sale_price <= 0
            """;

    /** Sale with the same property, date and price as an older sale row; the oldest row is the original. */
    String SALE_DUPLICATE = """
            SELECT d.id AS entity_id, d.property_id AS property_id, 'Duplicate of sale ' || d.first_id AS detail
            FROM (
                SELECT s.id, s.property_id,
                    MIN(s.id) OVER (PARTITION BY s.property_id, s.sale_date, s.sale_price) AS first_id
                FROM sales s
                WHERE s.property_id IS NOT NULL AND s.sale_date IS NOT NULL AND s.sale_price IS NOT NULL
            ) d
            WHERE d.id <> d.first_id
            """;

    /** Assessment with the same property and year as an older assessment row; the oldest row is the original. */
    String ASSESSMENT_DUPLICATE = """
            SELECT d.id AS entity_id, d.property_id AS property_id, 'Duplicate of assessment ' || d.first_id AS detail
            FROM (
                SELECT a.id, a.property_id,
                    MIN(a.id) OVER (PARTITION BY a.property_id, a.year) AS first_id
                FROM assessments a
                WHERE a.property_id IS NOT NULL AND a.year IS NOT NULL
            ) d
            WHERE d.id <> d.first_id
            """;

    /** Adds the folio and the first import mapping (provenance) to the rows of a rule. */
    String FIND_PREFIX = """
            SELECT i.entity_id AS "entityId", i.property_id AS "propertyId", p.folio AS "folio",
                i.detail AS "detail", m.source_county AS "sourceCounty", m.source_state AS "sourceState",
                m.source_property_id AS "sourcePropertyId"
            FROM (
            """;

    /** Closes {@link #FIND_PREFIX}: stable order by entity ID, bounded by limit and offset. */
    String FIND_SUFFIX = """
            ) i
            JOIN properties p ON p.id = i.property_id
            LEFT JOIN LATERAL (
                SELECT pim.source_county, pim.source_state, pim.source_property_id
                FROM property_import_mapping pim
                WHERE pim.geozentra_property_id = i.property_id
                ORDER BY pim.id
                LIMIT 1
            ) m ON true
            ORDER BY i.entity_id
            LIMIT :limit OFFSET :offset
            """;

    String COUNT_PREFIX = "SELECT COUNT(*) FROM (";
    String COUNT_SUFFIX = ") i";

    @Query(value = COUNT_PREFIX + MISSING_ADDRESS + COUNT_SUFFIX, nativeQuery = true)
    long countMissingAddress();

    @Query(value = FIND_PREFIX + MISSING_ADDRESS + FIND_SUFFIX, nativeQuery = true)
    List<DataQualityIssueRow> findMissingAddress(@Param("limit") int limit, @Param("offset") long offset);

    @Query(value = COUNT_PREFIX + MISSING_PARCEL + COUNT_SUFFIX, nativeQuery = true)
    long countMissingParcel();

    @Query(value = FIND_PREFIX + MISSING_PARCEL + FIND_SUFFIX, nativeQuery = true)
    List<DataQualityIssueRow> findMissingParcel(@Param("limit") int limit, @Param("offset") long offset);

    @Query(value = COUNT_PREFIX + STALE_ASSESSMENT + COUNT_SUFFIX, nativeQuery = true)
    long countStaleAssessment();

    @Query(value = FIND_PREFIX + STALE_ASSESSMENT + FIND_SUFFIX, nativeQuery = true)
    List<DataQualityIssueRow> findStaleAssessment(@Param("limit") int limit, @Param("offset") long offset);

    @Query(value = COUNT_PREFIX + LOT_SIZE_CONFLICT + COUNT_SUFFIX, nativeQuery = true)
    long countLotSizeConflict();

    @Query(value = FIND_PREFIX + LOT_SIZE_CONFLICT + FIND_SUFFIX, nativeQuery = true)
    List<DataQualityIssueRow> findLotSizeConflict(@Param("limit") int limit, @Param("offset") long offset);

    @Query(value = COUNT_PREFIX + SALE_PRICE_OUTLIER + COUNT_SUFFIX, nativeQuery = true)
    long countSalePriceOutlier();

    @Query(value = FIND_PREFIX + SALE_PRICE_OUTLIER + FIND_SUFFIX, nativeQuery = true)
    List<DataQualityIssueRow> findSalePriceOutlier(@Param("limit") int limit, @Param("offset") long offset);

    @Query(value = COUNT_PREFIX + SALE_DUPLICATE + COUNT_SUFFIX, nativeQuery = true)
    long countSaleDuplicate();

    @Query(value = FIND_PREFIX + SALE_DUPLICATE + FIND_SUFFIX, nativeQuery = true)
    List<DataQualityIssueRow> findSaleDuplicate(@Param("limit") int limit, @Param("offset") long offset);

    @Query(value = COUNT_PREFIX + ASSESSMENT_DUPLICATE + COUNT_SUFFIX, nativeQuery = true)
    long countAssessmentDuplicate();

    @Query(value = FIND_PREFIX + ASSESSMENT_DUPLICATE + FIND_SUFFIX, nativeQuery = true)
    List<DataQualityIssueRow> findAssessmentDuplicate(@Param("limit") int limit, @Param("offset") long offset);
}
