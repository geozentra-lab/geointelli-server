package com.geointelli.ai.property.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImage;
        
@Repository
public interface PropertyImageRepository extends JpaRepository<PropertyImage, Long> {
    Optional<PropertyImage> findByImageHash(String hash);
    Optional<PropertyImage> findByPropertyAndImageHash(Property property, String hash);
}
