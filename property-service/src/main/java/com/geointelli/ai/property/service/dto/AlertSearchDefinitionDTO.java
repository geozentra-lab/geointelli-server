package com.geointelli.ai.property.service.dto;

/** Membership identity excludes sorting and pagination because neither changes which properties match. */
public record AlertSearchDefinitionDTO(SavedSearchFilters filters, String hash, String stamp) {
}
