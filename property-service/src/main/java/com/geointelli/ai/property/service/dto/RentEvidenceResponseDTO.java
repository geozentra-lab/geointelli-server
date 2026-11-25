package com.geointelli.ai.property.service.dto;

import java.util.List;
import com.geointelli.ai.property.service.entity.RentEvidenceType;
import com.geointelli.ai.property.service.entity.RentGeographyLevel;

/**
 * Rent evidence of one geography as returned by {@code GET /api/rent-evidence} (SCRUM-132): the observations
 * matching the filters, newest period first, and an explicit coverage report so a missing figure is never read
 * as zero. {@code missingTypes} names the rent types (asking, achieved) we hold nothing for under the same
 * filters; {@code note} says it in words.
 */
public record RentEvidenceResponseDTO(
        RentGeographyLevel geographyLevel,
        String geographyCode,
        Coverage coverage,
        List<RentEvidenceType> availableTypes,
        List<RentEvidenceType> missingTypes,
        String note,
        PageResponse<RentEvidenceObservationDTO> observations) {

    /** Whether we hold rent evidence for the geography under the request's filters. */
    public enum Coverage {
        /** Every requested rent type has at least one observation. */
        AVAILABLE,
        /** Some requested rent types have observations, others none (listed in {@code missingTypes}). */
        PARTIAL,
        /** No observation at all for this geography under these filters. */
        NO_COVERAGE
    }
}
