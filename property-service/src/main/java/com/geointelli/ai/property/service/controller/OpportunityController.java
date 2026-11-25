package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.OpportunityBrowseRequest;
import com.geointelli.ai.property.service.dto.OpportunityPageDTO;
import com.geointelli.ai.property.service.dto.OpportunityScoreDTO;
import com.geointelli.ai.property.service.service.OpportunityBrowseService;
import com.geointelli.ai.property.service.service.OpportunityScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Investment opportunity scores (SCRUM-76): the live, explained score of one
 * property (SCRUM-167) and a paginated
 * browse of the stored scores (SCRUM-168). Requires an investor or admin realm
 * role;
 * shared property scores have no user ownership.
 */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Opportunities", description = "Versioned, explained opportunity scores built from the stored ZentraValue "
        + "valuation and forecasts. Unavailable evidence adds no points; without essential evidence a score is "
        + "unavailable. Invalid input 400; unknown property 404.")
public class OpportunityController {

    private final OpportunityScoringService opportunityScoringService;
    private final OpportunityBrowseService opportunityBrowseService;

    /**
     * Calculates the opportunity score of one property from its current stored
     * valuation.
     *
     * @param id
     *               property ID
     * @return 200 with the score, every factor and the coverage (status UNAVAILABLE
     *         with reasons when essential
     *         evidence is missing); 400 if the ID is invalid; 404 if the property
     *         does not exist
     */
    @GetMapping("/api/properties/id/{id}/opportunity-score")
    @Operation(summary = "Opportunity score of a property", description = "Scores the property now from its stored "
            + "valuation with the documented rules of scoreVersion. Each factor shows its evidence, subscore and points, "
            + "or why it is unavailable; coveragePercent is the share of the evidence that was available. Without a "
            + "supported valuation and its one-year forecast, status is UNAVAILABLE and score is null.")
    public OpportunityScoreDTO opportunityScore(@PathVariable Long id) {
        return opportunityScoringService.getScore(id);
    }

    /**
     * Lists scored properties, best first by default.
     *
     * @return 200 with one page; an empty page has status NO_MATCHES,
     *         NO_ELIGIBLE_OPPORTUNITIES or NOT_SCORED;
     *         400 if a parameter is out of range or the sort is not supported
     */
    @GetMapping("/api/opportunities")
    @Operation(summary = "Browse investment opportunities", description = "Pages through the stored scores of the "
            + "current scoring version (status SCORED only), each with its factors and coverage. Filters: countyId, "
            + "stateId, minScore (0-100), minValue/maxValue (estimated value, USD). sort: SCORE_DESC (default), "
            + "FORECAST_GROWTH_1Y_DESC, VALUE_ASC, VALUE_DESC; ties by property ID. page >= 0, size 1-100.")
    public OpportunityPageDTO opportunities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + OpportunityBrowseService.DEFAULT_SIZE) int size,
            @RequestParam(required = false) Long countyId,
            @RequestParam(required = false) Long stateId,
            @RequestParam(required = false) BigDecimal minScore,
            @RequestParam(required = false) BigDecimal minValue,
            @RequestParam(required = false) BigDecimal maxValue,
            @RequestParam(required = false) String sort) {
        return opportunityBrowseService.browse(
                new OpportunityBrowseRequest(page, size, countyId, stateId, minScore, minValue, maxValue, sort));
    }
}
