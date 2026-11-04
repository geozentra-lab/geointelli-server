package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.geointelli.ai.property.service.entity.TransportationAnalysis;

public interface TransportationAnalysisRepository extends JpaRepository<TransportationAnalysis, Long> {
    
    Optional<TransportationAnalysis> findByPropertyId(Long propertyId);
    List<TransportationAnalysis> findByPropertyIdIn(List<Long> propertyIds);
    List<TransportationAnalysis> findTop2000ByIdGreaterThanOrderByIdAsc(Long id);
    List<TransportationAnalysis> findTop2000ByTransportationScoreIsNullAndIdGreaterThanOrderByIdAsc(Long id);
}
