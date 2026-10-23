package com.geointelli.ai.property.service.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.geointelli.ai.property.service.entity.Property;

/** Reads scalar sale candidates without loading property collections. */
public interface ComparableCandidateRepository extends Repository<Property, Long> {
    /**
     * Uses an available parcel point, falling back to the parent's parcel for a child.
     */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM parcels pa
            WHERE (pa.property_id = :propertyId OR pa.property_id = :parentId)
              AND pa.geom IS NOT NULL AND NOT ST_IsEmpty(pa.geom)
              AND ST_SRID(pa.geom) = 4326 AND ST_IsValid(pa.geom)
        )
        """, nativeQuery = true)
    boolean hasGeometry(@Param("propertyId") Long propertyId, @Param("parentId") Long parentId);

    /**
     * Prefers shared-parent and community matches, then nearby sales.
     * Each property contributes only its latest eligible recorded transaction.
     */
    @Query(value = """
        WITH subject AS MATERIALIZED (
            SELECT p.*,
                ST_PointOnSurface(pa.geom) AS anchor,
                NULLIF(NULLIF(LOWER(BTRIM(p.subdivision)), ''), '0') AS community,
                NULLIF(NULLIF(LOWER(BTRIM(p.neighborhood)), ''), '0') AS neighborhood_code
            FROM properties p
            LEFT JOIN LATERAL (
                SELECT pa.geom FROM parcels pa
                WHERE (pa.property_id = p.id OR pa.property_id = p.parent_property_id)
                  AND pa.geom IS NOT NULL AND NOT ST_IsEmpty(pa.geom)
                  AND ST_SRID(pa.geom) = 4326 AND ST_IsValid(pa.geom)
                ORDER BY CASE WHEN pa.property_id = p.id THEN 0 ELSE 1 END, pa.fid
                LIMIT 1
            ) pa ON true
            WHERE p.id = :propertyId
        ), eligible AS (
            SELECT p.id, p.folio, p.county_id, p.parent_property_id, p.dor_code,
                p.bedroom_count, p.bathroom_count, p.building_heated_area, p.year_built,
                subject.anchor,
                CASE
                    WHEN p.parent_property_id IS NOT NULL
                      AND p.parent_property_id = subject.parent_property_id THEN 1
                    WHEN subject.community IS NOT NULL
                      AND LOWER(BTRIM(p.subdivision)) = subject.community THEN 2
                    WHEN subject.neighborhood_code IS NOT NULL
                      AND LOWER(BTRIM(p.neighborhood)) = subject.neighborhood_code THEN 3
                    ELSE 4
                END AS priority
            FROM subject
            JOIN properties p ON p.county_id = subject.county_id
                AND p.dor_code = subject.dor_code
                AND p.id <> subject.id
                AND p.id <> COALESCE(subject.parent_property_id, subject.id)
                AND (p.parent_property_id IS NULL OR p.parent_property_id <> subject.id)
            WHERE (subject.building_heated_area IS NULL OR subject.building_heated_area <= 0
                OR (p.building_heated_area > 0 AND p.building_heated_area BETWEEN
                    subject.building_heated_area * (1 - :areaTolerance / 100.0)
                    AND subject.building_heated_area * (1 + :areaTolerance / 100.0)))
              AND (subject.bedroom_count IS NULL
                OR ABS(p.bedroom_count - subject.bedroom_count) <= :bedroomTolerance)
        ), located AS MATERIALIZED (
            SELECT e.*, ST_PointOnSurface(pa.geom) AS candidate_point
            FROM eligible e
            LEFT JOIN LATERAL (
                SELECT pa.geom FROM parcels pa
                WHERE (pa.property_id = e.id OR pa.property_id = e.parent_property_id)
                  AND pa.geom IS NOT NULL AND NOT ST_IsEmpty(pa.geom)
                  AND ST_SRID(pa.geom) = 4326 AND ST_IsValid(pa.geom)
                ORDER BY CASE WHEN pa.property_id = e.id THEN 0 ELSE 1 END, pa.fid
                LIMIT 1
            ) pa ON true
        )
        SELECT e.id AS "propertyId", e.folio AS "folio", e.county_id AS "countyId",
            a.address AS "address", a.city AS "city", a.zip AS "zip",
            e.dor_code AS "dorCode", e.bedroom_count AS "bedrooms",
            e.bathroom_count AS "bathrooms", e.building_heated_area AS "livingAreaSqft",
            e.year_built AS "yearBuilt", sale.id AS "saleId",
            sale.sale_date AS "saleDate", sale.sale_price AS "salePrice",
            sale.qualified_flag AS "qualifiedFlag",
            sale.qualification_description AS "qualificationDescription",
            sale.instrument_number AS "instrumentNumber",
            CASE e.priority WHEN 1 THEN 'SAME_PARENT_GROUP' WHEN 2 THEN 'SAME_SUBDIVISION'
                WHEN 3 THEN 'SAME_NEIGHBORHOOD' ELSE 'NEARBY' END AS "matchLevel",
            ST_Distance(CAST(e.anchor AS geography), CAST(e.candidate_point AS geography)) AS "distanceMeters"
        FROM located e
        JOIN LATERAL (
            SELECT s.* FROM sales s
            WHERE s.property_id = e.id
              AND s.sale_date BETWEEN :dateFrom AND :asOfDate
              AND s.sale_price > 0 AND UPPER(BTRIM(s.qualified_flag)) = 'Q'
              AND LOWER(BTRIM(s.qualification_description)) IN (
                'sales which are qualified',
                'qual by exam of deed',
                'qual by verifiable & documented evidence'
              )
              AND (NULLIF(NULLIF(BTRIM(s.instrument_number), ''), '0') IS NULL OR NOT EXISTS (
                SELECT 1 FROM sales other
                JOIN properties other_property ON other_property.id = other.property_id
                WHERE other_property.county_id = e.county_id
                  AND other.property_id <> e.id
                  AND other.sale_date = s.sale_date
                  AND BTRIM(other.instrument_number) = BTRIM(s.instrument_number)
              ))
              AND (NULLIF(NULLIF(BTRIM(s.official_record_book), ''), '0') IS NULL
                OR NULLIF(NULLIF(BTRIM(s.official_record_page), ''), '0') IS NULL OR NOT EXISTS (
                  SELECT 1 FROM sales other
                  JOIN properties other_property ON other_property.id = other.property_id
                  WHERE other_property.county_id = e.county_id
                    AND other.property_id <> e.id
                    AND other.sale_date = s.sale_date
                    AND BTRIM(other.official_record_book) = BTRIM(s.official_record_book)
                    AND BTRIM(other.official_record_page) = BTRIM(s.official_record_page)
              ))
            ORDER BY s.sale_date DESC, s.id DESC LIMIT 1
        ) sale ON true
        LEFT JOIN LATERAL (
            SELECT a.address, a.city, a.zip FROM addresses a
            WHERE a.property_id = e.id ORDER BY a.id LIMIT 1
        ) a ON true
        WHERE e.priority < 4
           OR (e.anchor IS NOT NULL AND e.candidate_point IS NOT NULL
               AND ST_DWithin(CAST(e.anchor AS geography), CAST(e.candidate_point AS geography), :radiusMeters))
        ORDER BY e.priority, sale.sale_date DESC, "distanceMeters" ASC NULLS LAST, e.id, sale.id
        LIMIT :limit
        """, nativeQuery = true)
    List<ComparableCandidateProjection> findCandidates(@Param("propertyId") Long propertyId,
            @Param("dateFrom") LocalDate dateFrom, @Param("asOfDate") LocalDate asOfDate,
            @Param("radiusMeters") int radiusMeters, @Param("areaTolerance") int areaTolerance,
            @Param("bedroomTolerance") int bedroomTolerance, @Param("limit") int limit);
}
