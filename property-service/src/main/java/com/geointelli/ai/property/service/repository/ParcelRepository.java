package com.geointelli.ai.property.service.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.Parcel;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel,Long>{
    List<Parcel> findByFolioAndProperty_County_Id(String folio, Long countyId);

    @Query(value = """
                    SELECT *
                    FROM parcels p
                    WHERE p.geom && ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax, 4326)
                    AND ST_Intersects(p.geom, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax, 4326))
                    LIMIT 500
                    """, nativeQuery = true)
    List<Parcel> findWithinBoundingBox(@Param("xmin") double xmin,@Param("ymin") double ymin,@Param("xmax") double xmax,@Param("ymax") double ymax);

    @Query("SELECT DISTINCT p.folio FROM Parcel p WHERE p.property.county.id = :countyId")
    List<String> findAllFolios(@Param("countyId") Long countyId);

    // COUNT(*) lets PostgreSQL answer from the partial mapped-parcel index without reading rows.
    @Query(value = "SELECT COUNT(*) FROM parcels WHERE geom IS NOT NULL", nativeQuery = true)
    Long countByGeomIsNotNull();

    @Query("SELECT MAX(p.updatedAt) FROM Parcel p WHERE p.geom IS NOT NULL")
    LocalDateTime findMaxUpdatedAt();

    // LIMIT 1 per county stops at the first mapped parcel; a plain EXISTS is planned as a join over every parcel.
    @Query(value = """
            SELECT DISTINCT c.name FROM counties c
            CROSS JOIN LATERAL (
                SELECT 1 FROM properties pr JOIN parcels p ON p.property_id = pr.id
                WHERE pr.county_id = c.id AND p.geom IS NOT NULL
                LIMIT 1) mapped
            ORDER BY c.name
            """, nativeQuery = true)
    List<String> findDistinctCountyNames();

    @Query(value = """
                SELECT *
                FROM parcels p
                WHERE p.geom IS NOT NULL
                AND p.geom && ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax, 4326)
                AND ST_Intersects(p.geom, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax, 4326))
                ORDER BY p.fid
                LIMIT :limit
                """, nativeQuery = true)
    List<Parcel> findFeaturesWithinBoundingBox(@Param("xmin") double xmin,
                                               @Param("ymin") double ymin,
                                               @Param("xmax") double xmax,
                                               @Param("ymax") double ymax,
                                               @Param("limit") int limit);
}
