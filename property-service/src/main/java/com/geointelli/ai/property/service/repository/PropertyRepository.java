package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.Property;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long>{
    Optional<Property> findByFolio(String folio);

    @Query("SELECT p.folio FROM Property p")
    List<String> findAllFolios();

    @Query("SELECT p.id from Property p")
    List<Long> findAllIds();

    @Query("SELECT p.folio FROM Property p WHERE p.address IS NULL")
    List<String> findFoliosWithoutAddress();

    @Query("""
        SELECT a.property
        FROM Address a
        WHERE LOWER(a.address) LIKE LOWER(CONCAT(:address, '%'))
        AND LOWER(a.zip) LIKE LOWER(CONCAT(:zip, '%'))
    """)
    Optional<Property> findByAddress_ZipAndAddress_Address(String zip, String address);

    @Query("SELECT p FROM Property p JOIN FETCH p.images LEFT JOIN FETCH p.address")
    List<Property> findAllWithImagesAndAddress();
}