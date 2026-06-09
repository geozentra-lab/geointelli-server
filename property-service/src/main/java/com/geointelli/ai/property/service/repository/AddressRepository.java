package com.geointelli.ai.property.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.Address;
import com.geointelli.ai.property.service.entity.Property;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    @Query("SELECT a.property.id FROM Address a")
    List<Long> findAllPropertiesId();

    @Query("""
        SELECT a.property FROM Address a
        WHERE (:streetNumber IS NULL OR a.streetNumber = :streetNumber)
        AND (:streetName IS NULL OR LOWER(a.streetName) LIKE CONCAT('%', :streetName, '%'))
        AND (:city IS NULL OR LOWER(a.city) LIKE CONCAT('%', :city, '%'))
        AND (:zip IS NULL OR a.zip = :zip)
        AND (:unit IS NULL OR LOWER(a.unit) LIKE CONCAT('%', :unit, '%'))
        AND (:state IS NULL OR LOWER(a.streetPrefix) LIKE CONCAT('%', :state, '%'))
        """)
    List<Property> findPropertyByAddressFields(
            @Param("streetNumber") Integer streetNumber,
            @Param("streetName")   String streetName,
            @Param("city")         String city,
            @Param("zip")          String zip,
            @Param("unit")         String unit,
            @Param("state")        String state
    );

      @Query("""
        SELECT a.property
        FROM Address a
        WHERE
        LOWER(
                REPLACE(
                REPLACE(
                        REPLACE(a.address, ',', ''),
                '.', ''),
                ':', '')
        )
        LIKE
        LOWER(
                CONCAT(
                '%',
                REPLACE(
                        REPLACE(
                        REPLACE(:raw, ',', ''),
                        '.', ''),
                ':', ''),
                '%'
                )
        )
        """)    
        List<Property> findPropertiesByRawAddress(@Param("raw") String raw);

        @Query(value = """
        SELECT a.address
        FROM addresses a
        WHERE :raw <% a.address
        ORDER BY word_similarity(:raw, a.address) DESC
        LIMIT 10
        """, nativeQuery = true)
        List<String> findAddressSuggestions(@Param("raw") String raw);
}