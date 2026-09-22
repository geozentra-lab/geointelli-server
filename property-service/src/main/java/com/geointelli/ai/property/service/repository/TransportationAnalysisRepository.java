package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.geointelli.ai.property.service.entity.TransportationAnalysis;

public interface TransportationAnalysisRepository extends JpaRepository<TransportationAnalysis, Long> {
    
    Optional<TransportationAnalysis> findByPropertyId(Long propertyId);
    List<TransportationAnalysis> findByPropertyIdIn(List<Long> propertyIds);
    Page<TransportationAnalysis> findByTransportationScoreIsNull(Pageable page);
}
