package com.geointelli.ai.property.service.dto;

import java.util.List;

/** Owner-scoped rules, ordered by descending ID with bounded zero-based pagination. */
public record AlertRulePageDTO(List<AlertRuleDTO> content, int page, int size, long totalElements, int totalPages) {
}
