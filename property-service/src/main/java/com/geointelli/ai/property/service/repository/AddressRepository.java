package com.geointelli.ai.property.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    @Query("SELECT a.property.id FROM Address a")
    List<Long> findAllPropertiesId();

    /**
     * Property IDs whose address fields match, from the first {@code candidates} matches found, ordered by ID.
     * Capping candidates keeps broad filters such as a city from walking the whole table.
     */
    @Query(value = """
        SELECT c.property_id FROM (
            SELECT a.property_id FROM addresses a
            LEFT JOIN properties p ON p.id = a.property_id
            LEFT JOIN counties co ON co.id = p.county_id
            LEFT JOIN states s ON s.id = co.state_id
            WHERE a.property_id IS NOT NULL
            AND (CAST(:streetNumber AS integer) IS NULL OR a.street_number = CAST(:streetNumber AS integer))
            AND (CAST(:streetName AS text) IS NULL OR LOWER(a.street_name) LIKE '%' || CAST(:streetName AS text) || '%')
            AND (CAST(:city AS text) IS NULL OR LOWER(a.city) LIKE '%' || CAST(:city AS text) || '%')
            AND (CAST(:zip AS text) IS NULL OR LOWER(a.zip) = CAST(:zip AS text))
            AND (CAST(:unit AS text) IS NULL OR LOWER(a.unit) LIKE '%' || CAST(:unit AS text) || '%')
            AND (CAST(:state AS text) IS NULL OR LOWER(s.code) = CAST(:state AS text) OR LOWER(s.name) = CAST(:state AS text))
            LIMIT :candidates) c
        ORDER BY c.property_id
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> findPropertyIdsByAddressFields(
            @Param("streetNumber") Integer streetNumber,
            @Param("streetName")   String streetName,
            @Param("city")         String city,
            @Param("zip")          String zip,
            @Param("unit")         String unit,
            @Param("state")        String state,
            @Param("candidates")   int candidates,
            @Param("limit")        int limit
    );

    /**
     * Property IDs whose address, ignoring case, commas, dots and colons, matches {@code pattern};
     * closest matches first, ranked among the first {@code candidates} matches found.
     * The match expression must stay identical to the trigram index from V29 so the index is used.
     */
    @Query(value = """
        SELECT c.property_id FROM (
            SELECT a.property_id, a.address FROM addresses a
            WHERE a.property_id IS NOT NULL
            AND LOWER(REPLACE(REPLACE(REPLACE(a.address, ',', ''), '.', ''), ':', '')) LIKE :pattern ESCAPE '\\'
            LIMIT :candidates) c
        ORDER BY word_similarity(:raw, c.address) DESC, c.property_id
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> findPropertyIdsByRawAddress(@Param("raw") String raw, @Param("pattern") String pattern,
            @Param("candidates") int candidates, @Param("limit") int limit);

    @Query(value = """
        SELECT a.address
        FROM addresses a
        WHERE :raw <% a.address
        ORDER BY word_similarity(:raw, a.address) DESC
        LIMIT 10
        """, nativeQuery = true)
        List<String> findAddressSuggestions(@Param("raw") String raw);
}