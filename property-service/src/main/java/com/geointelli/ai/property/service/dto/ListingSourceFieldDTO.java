package com.geointelli.ai.property.service.dto;

import com.geointelli.ai.property.service.entity.ListingFieldAvailability;
import com.geointelli.ai.property.service.entity.ListingFieldGroup;
import com.geointelli.ai.property.service.entity.ListingSourceField;

/**
 * One line of a listing provider's field mapping as returned by the admin endpoints (SCRUM-118): the provider
 * field, the column of the listings table it maps to (null when not imported), its type and availability.
 */
public record ListingSourceFieldDTO(
    Long id,
    String sourceCode,
    ListingFieldGroup fieldGroup,
    String sourceField,
    String targetColumn,
    String dataType,
    ListingFieldAvailability availability,
    String note,
    Integer displayOrder) {

    /** Builds the DTO from the entity; the source code is taken from the parent source. */
    public static ListingSourceFieldDTO from(ListingSourceField f) {
        return new ListingSourceFieldDTO(
            f.getId(),
            f.getSource().getCode(),
            f.getFieldGroup(),
            f.getSourceField(),
            f.getTargetColumn(),
            f.getDataType(),
            f.getAvailability(),
            f.getNote(),
            f.getDisplayOrder());
    }
}
