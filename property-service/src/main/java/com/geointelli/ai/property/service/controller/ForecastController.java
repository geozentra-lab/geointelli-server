package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.ForecastResponse;
import com.geointelli.ai.property.service.service.ForecastService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

/** Dedicated read API for stored property forecasts. */
@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class ForecastController {
    private final ForecastService forecastService;

    /**
     * Returns model projections separately from observed property history.
     *
     * @param id
     *               positive property ID
     * @return stored forecasts and their support, uncertainty and provenance
     */
    @GetMapping("/id/{id}/forecast")
    @Operation(summary = "Read property forecasts", description = "Stored 1/3/5-year USD projections; no recalculation "
            + "or comparable-sales query. Three/five-year projections are trend scenarios and all forecast bounds "
            + "are illustrative. Confidence is inherited from the valuation. Limited output retains its caveats; "
            + "unsupported or missing forecasts have available=false. Missing horizon values remain null. "
            + "status describes valuation support; available describes forecast availability. "
            + "predictionDate and dataThrough describe model freshness. No horizon query parameter is required. The 5-year "
            + "forecast replaced the 7-year one: sevenYears appears (and fiveYears is omitted) only while a "
            + "property's stored valuation predates that change.")
    @ApiResponse(responseCode = "200", description = "Stored forecasts or explicit unavailability")
    @ApiResponse(responseCode = "400", description = "Invalid property ID")
    @ApiResponse(responseCode = "404", description = "Property does not exist")
    public ForecastResponse getForecast(@PathVariable Long id) {
        return forecastService.getForecast(id);
    }
}
