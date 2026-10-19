package com.geointelli.ai.property.service.repository;

import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.Property;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long>{
    Optional<Property> findByFolioAndCounty_Id(String folio, Long countyId);

    @Query("SELECT p.folio FROM Property p WHERE p.county.id = :countyId")
    List<String> findAllFolios(@Param("countyId") Long countyId);

    @Query("SELECT p.id from Property p")
    List<Long> findAllIds();

    @Query("SELECT p.folio FROM Property p WHERE p.county.id = :countyId AND p.address IS NULL")
    List<String> findFoliosWithoutAddress(@Param("countyId") Long countyId);

    @Query("SELECT p.folio FROM Property p WHERE p.county.id = :countyId AND p.extraFeatures IS EMPTY")
    List<String> findFoliosWithoutExtraFeature(@Param("countyId") Long countyId);

    @Query("SELECT p.folio FROM Property p WHERE p.county.id = :countyId AND p.sales IS EMPTY")
    List<String> findFoliosWithoutSales(@Param("countyId") Long countyId);

    @Query("""
        SELECT a.property
        FROM Address a
        WHERE LOWER(a.address) LIKE LOWER(CONCAT(:address, '%'))
        AND LOWER(a.zip) LIKE LOWER(CONCAT(:zip, '%'))
    """)
    Optional<Property> findByAddress_ZipAndAddress_Address(String zip, String address);

    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.images LEFT JOIN FETCH p.address")
    List<Property> findAllWithImagesAndAddress();

    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.address")
    List<Property> findAllWithAddress();
}