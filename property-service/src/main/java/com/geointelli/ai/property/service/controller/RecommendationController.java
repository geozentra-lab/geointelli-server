package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.RecommendationResponseDTO;
import com.geointelli.ai.property.service.service.RecommendationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Exposes current-user property research recommendations. */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {
    private final RecommendationService recommendationService;

    /**
     * Returns bounded suggestions using only the authenticated user's saved preferences.
     *
     * @param limit maximum number of suggestions (default 20, maximum 50)
     * @return property cards and reasons, with unsupported preferences reported explicitly
     */
    @GetMapping
    @Operation(summary = "Recommend properties from my saved preferences", description = "Uses the current user's "
        + "target county IDs saved through PUT /api/users/me/preferences. No userId is accepted. "
        + "All county matches have equal relevance; property ID ascending breaks ties. No count query or external API is used. "
        + "NO_PREFERENCES and LOCATION_REQUIRED return empty content without a global fallback; NO_MATCHES means no local "
        + "property records in those counties. PARTIAL means saved budget or goals could not be evaluated. "
        + "READY only means all requested, supported preference criteria were applied, not that these are active listings. "
        + "Current prices, availability, source freshness and opportunity scores are unavailable. Budget is not inferred "
        + "from historical sales or assessments; goals are not parsed as filters. Results can include parent and child "
        + "properties as separate internal IDs, matching property search behavior. Limit is 1 to 50; this endpoint is not paginated.")
    @ApiResponse(responseCode = "200", description = "Recommendations, partial evidence, cold start or empty results")
    @ApiResponse(responseCode = "400", description = "Invalid limit")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer identity")
    public RecommendationResponseDTO recommend(@RequestParam(defaultValue = "20") int limit) {
        return recommendationService.recommend(limit);
    }
}
