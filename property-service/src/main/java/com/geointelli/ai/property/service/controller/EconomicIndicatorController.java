package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.EconomicIndicatorDTO;
import com.geointelli.ai.property.service.dto.EconomicIndicatorObservationsDTO;
import com.geointelli.ai.property.service.service.EconomicIndicatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Stored economic indicators (SCRUM-133): public reference data, read by the market outputs. No user owns it.
 */
@RestController
@RequestMapping("/api/economic-indicators")
@RequiredArgsConstructor
@Tag(name = "Economic indicators", description = "County economic indicators imported from the BLS and the Census "
        + "Bureau, with units, periods, provenance and revisions.")
public class EconomicIndicatorController {

    private final EconomicIndicatorService economicIndicatorService;

    /**
     * Every supported indicator with its definition and coverage.
     *
     * @return 200 with the indicators
     */
    @GetMapping
    @Operation(summary = "List economic indicators", description = "Definition (unit, frequency, geography, "
            + "source) and coverage (counties, first and last period, estimates, last import) of each supported "
            + "indicator.")
    public List<EconomicIndicatorDTO> getIndicators() {
        return economicIndicatorService.getIndicators();
    }

    /**
     * The stored values of one indicator for one county.
     *
     * @param code     indicator code, for example UNEMPLOYMENT_RATE
     * @param fips     5-digit county FIPS code, for example 12086
     * @param fromYear only periods ending in this year or later
     * @param toYear   only periods ending in this year or earlier
     * @return 200 with the values (status EMPTY when none); 400 for an invalid fips or years; 404 for an unknown code
     */
    @GetMapping("/{code}/observations")
    @Operation(summary = "Values of an indicator for a county", description = "Oldest period first, each with its "
            + "provenance (source, vintage, import times) and revision history (previous value, revision count).")
    @ApiResponse(responseCode = "200", description = "Values, or status EMPTY when none is stored")
    @ApiResponse(responseCode = "400", description = "Invalid fips or years")
    @ApiResponse(responseCode = "404", description = "Unknown indicator")
    public EconomicIndicatorObservationsDTO getObservations(@PathVariable String code,
                                                            @RequestParam String fips,
                                                            @RequestParam(required = false) Integer fromYear,
                                                            @RequestParam(required = false) Integer toYear) {
        return economicIndicatorService.getObservations(code, fips, fromYear, toYear);
    }
}
