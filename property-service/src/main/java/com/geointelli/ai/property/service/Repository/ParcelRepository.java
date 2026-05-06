package com.geointelli.ai.property.service.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.Parcel;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel,Long>{
    List<Parcel> findByFolio(String folio);

    List<Parcel> findAllByFolio(String folio);

    @Query(value = """
                    SELECT *
                    FROM parcels p
                    WHERE p.geom && ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax, 4326)
                    AND ST_Intersects(p.geom, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax, 4326))
                    LIMIT 500
                    """, nativeQuery = true)
    List<Parcel> findWithinBoundingBox(@Param("xmin") double xmin,@Param("ymin") double ymin,@Param("xmax") double xmax,@Param("ymax") double ymax);

    @Query("SELECT folio from Parcel p")
    List<String> findAllFolios();
}
