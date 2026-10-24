package com.geointelli.ai.property.service.dto;

import java.util.List;

/** A bounded page, using zero-based page numbers and a stable descending ID order. */
public record PortfolioPageDTO<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
