package com.geointelli.ai.property.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.PropertyImportMapping;

@Repository 
public interface PropertyImportMappingRepository extends JpaRepository<PropertyImportMapping, Long> {
    Optional<PropertyImportMapping> findBySourceCountyAndSourceStateAndSourcePropertyId(
        String sourceCounty,
        String sourceState,
        Long sourcePropertyId
    );
}
