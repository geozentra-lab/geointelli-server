package com.geointelli.ai.property.service.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.County;

public interface CountyRepository extends JpaRepository<County, Long> {
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
}
