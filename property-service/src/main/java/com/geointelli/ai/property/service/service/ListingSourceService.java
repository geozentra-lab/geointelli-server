package com.geointelli.ai.property.service.service;

import java.util.List;
import com.geointelli.ai.property.service.dto.ListingSourceDTO;
import com.geointelli.ai.property.service.dto.ListingSourceFieldDTO;

/**
 * Read access to the listing providers and their field mapping (SCRUM-118), the machine-readable copy of
 * {@code docs/listing-source-field-mapping.md}. Rows are seeded by migration; nothing is written here.
 */
public interface ListingSourceService {

    /** All providers, newest first, each with its field count and without the field list. */
    List<ListingSourceDTO> list();

    /**
     * One provider with its full field mapping in document order.
     *
     * @param code provider code, any letter case
     * @throws com.geointelli.ai.property.service.exception.ListingSourceNotFoundException when no provider has
     *         that code (404)
     */
    ListingSourceDTO get(String code);

    /**
     * Field mapping rows of one provider, optionally filtered.
     *
     * @param code         provider code, any letter case
     * @param group        field group name ({@code IDENTIFIER}, {@code ASKING_PRICE}, ...) or null for all
     * @param availability availability name ({@code AVAILABLE}, {@code EXCLUDED}, ...) or null for all
     * @throws IllegalArgumentException when a filter is not one of the allowed names (400)
     * @throws com.geointelli.ai.property.service.exception.ListingSourceNotFoundException when no provider has
     *         that code (404)
     */
    List<ListingSourceFieldDTO> fields(String code, String group, String availability);
}
