package com.geointelli.ai.property.service.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.geointelli.ai.property.service.entity.ListingFieldAvailability;
import com.geointelli.ai.property.service.entity.ListingFieldGroup;
import com.geointelli.ai.property.service.entity.ListingSourceField;

/** Field mapping rows of a listing provider (SCRUM-118). */
@Repository
public interface ListingSourceFieldRepository extends JpaRepository<ListingSourceField, Long> {

    /**
     * Mapping rows of one provider in document order, optionally narrowed to one group and/or one availability.
     * A null filter means "any".
     */
    @Query("""
        SELECT f FROM ListingSourceField f
        WHERE f.source.id = :sourceId
          AND (:fieldGroup IS NULL OR f.fieldGroup = :fieldGroup)
          AND (:availability IS NULL OR f.availability = :availability)
        ORDER BY f.displayOrder ASC
        """)
    List<ListingSourceField> findFields(@Param("sourceId") Long sourceId,
                                        @Param("fieldGroup") ListingFieldGroup fieldGroup,
                                        @Param("availability") ListingFieldAvailability availability);

    /** Number of mapping rows of one provider. */
    long countBySourceId(Long sourceId);
}
