package com.geointelli.ai.property.service.dto;

import java.util.List;

/** Zero-based bounded page; every list is ordered by ID descending. */
public record CrmPageDTO<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
