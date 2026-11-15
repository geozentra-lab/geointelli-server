package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

/**
 * Query of {@code GET /api/opportunities} (SCRUM-168). Every filter is optional; null means not applied.
 *
 * @param page     zero-based page number
 * @param size     page size, 1 to 100
 * @param countyId only properties in this county
 * @param stateId  only properties in this state
 * @param minScore only scores of at least this value, 0 to 100
 * @param minValue only estimated values of at least this amount (USD)
 * @param maxValue only estimated values of at most this amount (USD)
 * @param sort     SCORE_DESC (default), FORECAST_GROWTH_1Y_DESC, VALUE_ASC or VALUE_DESC
 */
public record OpportunityBrowseRequest(int page, int size, Long countyId, Long stateId, BigDecimal minScore,
                                       BigDecimal minValue, BigDecimal maxValue, String sort) {
}
