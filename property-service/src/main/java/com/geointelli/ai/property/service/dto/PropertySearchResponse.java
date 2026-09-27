package com.geointelli.ai.property.service.dto;

import java.util.List;

public record PropertySearchResponse(
        List<PropertySearchItem> content, int page, int size,
        long totalElements, long totalPages, boolean first, boolean last) {
}
