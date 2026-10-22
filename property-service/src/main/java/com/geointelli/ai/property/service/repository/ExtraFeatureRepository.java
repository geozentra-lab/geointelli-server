package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.ExtraFeature;

@Repository
public interface ExtraFeatureRepository extends JpaRepository<ExtraFeature, Long> {

    @Query("SELECT MAX(e.rollYear) FROM ExtraFeature e WHERE e.property.id = :propertyId")
    Optional<Integer> findMaxRollYearByPropertyId(@Param("propertyId") Long propertyId);

    List<ExtraFeature> findByProperty_IdAndRollYearOrderByIdAsc(Long propertyId, Integer rollYear);

    @Query("SELECT e FROM ExtraFeature e WHERE e.property.id = :propertyId "
         + "ORDER BY e.rollYear DESC NULLS LAST, e.id ASC")
    Page<ExtraFeature> findHistoryByPropertyId(@Param("propertyId") Long propertyId, Pageable pageable);
}
