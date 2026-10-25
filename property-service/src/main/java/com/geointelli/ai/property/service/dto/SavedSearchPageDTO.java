package com.geointelli.ai.property.service.dto;

import java.util.List;

/** Owned definitions ordered by descending ID, with zero-based pagination. */
public record SavedSearchPageDTO(List<SavedSearchDTO> content, int page, int size, long totalElements, int totalPages) {
}
