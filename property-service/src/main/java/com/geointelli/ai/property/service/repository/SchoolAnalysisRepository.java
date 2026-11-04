package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.geointelli.ai.property.service.entity.SchoolAnalysis;


public interface SchoolAnalysisRepository extends JpaRepository<SchoolAnalysis, Long> {
    Optional<SchoolAnalysis> findByPropertyId(Long propertyId);
    List<SchoolAnalysis> findByPropertyIdIn(List<Long> propertyIds);
    List<SchoolAnalysis> findTop2000ByIdGreaterThanOrderByIdAsc(Long id);
    List<SchoolAnalysis> findTop2000BySchoolScoreIsNullAndIdGreaterThanOrderByIdAsc(Long id);
}
