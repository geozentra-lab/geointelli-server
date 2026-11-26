package com.geointelli.ai.property.service.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.County;

public interface CountyRepository extends JpaRepository<County, Long> {
    @EntityGraph(attributePaths = "state")
    List<County> findByIdIn(List<Long> ids);

    @EntityGraph(attributePaths = "state")
    List<County> findByState_IdOrderByNameAsc(Long stateId);

    @Modifying
    @Query(value = """
        INSERT INTO states (code, name) VALUES (:code, :name)
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    void insertStateIfAbsent(@Param("code") String code, @Param("name") String name);

    @Modifying
    @Query(value = """
        INSERT INTO counties (state_id, name)
        SELECT id, :county FROM states WHERE code = :code
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    void insertCountyIfAbsent(@Param("county") String county, @Param("code") String code);

    @Query("""
        SELECT c FROM County c JOIN FETCH c.state s
        WHERE LOWER(c.name) = LOWER(:county)
          AND (LOWER(s.code) = LOWER(:state) OR LOWER(s.name) = LOWER(:state))
        """)
    Optional<County> findByCountyAndState(@Param("county") String county, @Param("state") String state);

    /** ID of the county with this five-digit FIPS code ({@code counties.fips_code}, migration V42). */
    @Query(value = "SELECT id FROM counties WHERE fips_code = :fips", nativeQuery = true)
    Optional<Long> findIdByFipsCode(@Param("fips") String fips);

    /**
     * The county whose five-digit FIPS code ({@code counties.fips_code}, migration V42) is {@code fips}, used to
     * link county- and tract-level reference rows (SCRUM-132). Empty when no county carries that code.
     */
    @Query(value = "SELECT * FROM counties WHERE fips_code = :fips", nativeQuery = true)
    Optional<County> findByFipsCode(@Param("fips") String fips);
}
