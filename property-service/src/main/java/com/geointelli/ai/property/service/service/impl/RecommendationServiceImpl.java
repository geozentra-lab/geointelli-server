package com.geointelli.ai.property.service.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.PropertySearchItem;
import com.geointelli.ai.property.service.dto.RecommendationResponseDTO;
import com.geointelli.ai.property.service.dto.RecommendationResponseDTO.Item;
import com.geointelli.ai.property.service.dto.RecommendationResponseDTO.Status;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.UserPreferences;
import com.geointelli.ai.property.service.repository.UserPreferencesRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PropertySearchService;
import com.geointelli.ai.property.service.service.RecommendationService;

import lombok.RequiredArgsConstructor;

/** Uses stored county relationships; free-text goals never become invented search filters. */
@Service
@RequiredArgsConstructor
// Resolving a first-time authenticated user can insert their local user record.
@Transactional
public class RecommendationServiceImpl implements RecommendationService {
    private final CurrentUserService currentUserService;
    private final UserPreferencesRepository preferencesRepository;
    private final PropertySearchService propertySearchService;

    /** {@inheritDoc} */
    @Override
    public RecommendationResponseDTO recommend(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        Long userId = currentUserService.getCurrentUserEntity().getId();
        UserPreferences preferences = preferencesRepository.findById(userId).orElse(null);
        List<String> limitations = new ArrayList<>();
        limitations.add("Property records are research suggestions, not confirmed active listings. Listing availability and prices are unavailable.");
        limitations.add("Source freshness is unavailable; generatedAt is the response time, not the property data's observation time.");
        if (preferences == null) {
            limitations.add("Save target locations in your user preferences to receive recommendations.");
            return response(Status.NO_PREFERENCES, limit, null, List.of(), limitations, List.of());
        }

        List<Long> countyIds = preferences.getTargetCounties().stream().map(County::getId).distinct().toList();
        boolean hasBudget = preferences.getMinBudget() != null || preferences.getMaxBudget() != null;
        boolean hasGoals = !preferences.getInvestmentGoals().isEmpty();
        if (hasBudget) {
            limitations.add("Budget was not applied: current listing prices are unavailable. Returned properties are not verified to fit your budget; amounts are not inferred from assessments or past sales.");
        }
        if (hasGoals) {
            limitations.add("Investment goals were not scored: structured goal criteria and opportunity scores are unavailable.");
        }
        if (countyIds.isEmpty()) {
            limitations.add("Choose at least one target county; no nationwide fallback is used.");
            return response(Status.LOCATION_REQUIRED, limit, preferences, countyIds, limitations, List.of());
        }

        List<PropertySearchItem> candidates = propertySearchService.findRecommendationCandidates(countyIds, limit);
        List<Item> items = new ArrayList<>();
        for (PropertySearchItem property : candidates) {
            items.add(new Item(items.size() + 1, property,
                List.of("Matches your saved target county: " + property.countyName() + ", " + property.stateCode()),
                hasBudget ? "UNKNOWN" : "NOT_REQUESTED", hasGoals ? "UNKNOWN" : "NOT_REQUESTED", "UNKNOWN"));
        }
        Status status = items.isEmpty() ? Status.NO_MATCHES : hasBudget || hasGoals ? Status.PARTIAL : Status.READY;
        return response(status, limit, preferences, countyIds, limitations, items);
    }

    private RecommendationResponseDTO response(Status status, int limit, UserPreferences preferences,
            List<Long> counties, List<String> limitations, List<Item> items) {
        return new RecommendationResponseDTO(status, Instant.now(), "PROPERTY_RECORDS", null,
            "COUNTY_MATCH_THEN_PROPERTY_ID_ASC", limit, counties,
            preferences == null ? null : preferences.getMinBudget(),
            preferences == null ? null : preferences.getMaxBudget(),
            preferences == null ? List.of() : List.copyOf(preferences.getInvestmentGoals()),
            List.copyOf(limitations), List.copyOf(items));
    }
}
