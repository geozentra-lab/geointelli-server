package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImage;
        
@Repository
public interface PropertyImageRepository extends JpaRepository<PropertyImage, Long> {
    Optional<PropertyImage> findByImageHash(String hash);
    
    Optional<PropertyImage> findByPropertyAndImageHash(Property property, String hash);

    @Query("SELECT pi.imageHash FROM PropertyImage pi WHERE pi.property = :property")
    Set<String> findHashesByProperty(@Param("property") Property property);

    List<PropertyImage> findByPropertyId(Long propertyId);
}
