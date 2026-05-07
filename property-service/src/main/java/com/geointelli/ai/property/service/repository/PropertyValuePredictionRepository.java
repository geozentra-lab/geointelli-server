package com.geointelli.ai.property.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.PropertyValuePrediction;

@Repository
public interface PropertyValuePredictionRepository extends JpaRepository<PropertyValuePrediction,Long> {
    Optional<PropertyValuePrediction> findByPropertyId(Long propertyId);
}
