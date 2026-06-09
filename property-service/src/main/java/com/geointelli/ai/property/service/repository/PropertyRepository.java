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

    @Query("SELECT folio FROM Property p")
    List<String> findAllFolios();

    @Query("SELECT p.id from Property p")
    List<Long> findAllIds();

    @Query("SELECT p.folio FROM Property p WHERE p.address IS NULL")
    List<String> findFoliosWithoutAddress();

    Optional<Property> findByAddress_ZipAndAddress_Address(String zip, String address);
}