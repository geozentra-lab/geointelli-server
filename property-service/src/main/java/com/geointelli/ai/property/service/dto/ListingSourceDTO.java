package com.geointelli.ai.property.service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.geointelli.ai.property.service.entity.ListingAuthorizationStatus;
import com.geointelli.ai.property.service.entity.ListingRefreshMethod;
import com.geointelli.ai.property.service.entity.ListingSource;

/**
 * A listing provider as returned by the admin endpoints (SCRUM-118). The list endpoint returns the source with
 * its {@code fieldCount} and no {@code fields}; the single-source endpoint adds the full field mapping in
 * document order.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListingSourceDTO(
    Long id,
    String code,
    String name,
    String provider,
    String accessMethod,
    ListingAuthorizationStatus authorizationStatus,
    LocalDate authorizedAt,
    String licenceNote,
    String coverage,
    ListingRefreshMethod refreshMethod,
    String refreshCadence,
    String identityFields,
    String knownLimits,
    long fieldCount,
    List<ListingSourceFieldDTO> fields,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

    /** Builds the DTO from the entity with the given field count and field list (null to leave the list out). */
    public static ListingSourceDTO from(ListingSource s, long fieldCount, List<ListingSourceFieldDTO> fields) {
        return new ListingSourceDTO(
            s.getId(),
            s.getCode(),
            s.getName(),
            s.getProvider(),
            s.getAccessMethod(),
            s.getAuthorizationStatus(),
            s.getAuthorizedAt(),
            s.getLicenceNote(),
            s.getCoverage(),
            s.getRefreshMethod(),
            s.getRefreshCadence(),
            s.getIdentityFields(),
            s.getKnownLimits(),
            fieldCount,
            fields,
            s.getCreatedAt(),
            s.getUpdatedAt());
    }
}
