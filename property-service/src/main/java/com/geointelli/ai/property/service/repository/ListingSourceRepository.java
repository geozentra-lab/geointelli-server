package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.geointelli.ai.property.service.entity.ListingSource;

/** Listing providers and their authorization (SCRUM-118). */
@Repository
public interface ListingSourceRepository extends JpaRepository<ListingSource, Long> {

    /** Finds a provider by its code, in any letter case. */
    Optional<ListingSource> findByCodeIgnoreCase(String code);

    /** All providers, newest first. */
    List<ListingSource> findAllByOrderByCreatedAtDesc();
}
