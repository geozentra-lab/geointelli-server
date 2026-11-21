package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.PriceDivergenceDTO;
import com.geointelli.ai.property.service.service.PriceDivergenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Property price divergence. Property data is public, like the valuation endpoint, so no token is required and no
 * ownership applies.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Price divergence", description = "Explains how far a property's ZentraValue estimate is from its "
        + "building, community and micro-market benchmarks. Invalid input 400; unknown property 404.")
public class PriceDivergenceController {

    private final PriceDivergenceService priceDivergenceService;

    /**
     * Explains the price divergence of one property.
     *
     * @param id property ID
     * @return 200 with the benchmarks, drivers, residual and confidence (UNAVAILABLE with a reason when evidence is
     *         missing); 400 if the ID is invalid; 404 if the property does not exist
     */
    @GetMapping("/api/properties/id/{id}/price-divergence")
    @Operation(summary = "Price divergence of a property", description = "Benchmarks are the median stored ZentraValue "
            + "estimates of the other properties of the same building (parent property), community (subdivision) and "
            + "micro-market (neighborhood), with at least 5 valued properties. primaryBenchmark is the most specific "
            + "available level; each benchmark also shows the group's averageValue. drivers compare the property's "
            + "valuation factors with the group's average factors and explain divergenceFromAverageUsd (estimate minus "
            + "the average estimate of the same members); unexplainedResidual is the part of that gap no single factor "
            + "accounts for, because the model multiplies its factors. No percentage is estimated. "
            + "askingPriceComparison is separate and UNAVAILABLE because no asking price is recorded.")
    public PriceDivergenceDTO priceDivergence(@PathVariable Long id) {
        return priceDivergenceService.getDivergence(id);
    }
}
