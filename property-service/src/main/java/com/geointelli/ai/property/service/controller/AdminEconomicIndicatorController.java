package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.EconomicIndicatorImportResultDTO;
import com.geointelli.ai.property.service.service.EconomicIndicatorImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Imports of the economic indicators (SCRUM-133). {@code /api/admin/**} requires the admin role. Each import reads
 * its provider and waits until the values are stored.
 */
@RestController
@RequestMapping("/api/admin/economic-indicators")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin economic indicators", description = "Import county economic indicators from the BLS and the "
        + "Census Bureau. Admin role required.")
public class AdminEconomicIndicatorController {

    private final EconomicIndicatorImportService importService;

    /**
     * Imports the monthly labor force, employment and unemployment rate from the BLS LAUS API.
     *
     * @param state    state code or name, for example FL
     * @param fips     one county only, for example 12086; omitted for every county of the state
     * @param fromYear first year; omitted for two years before toYear
     * @param toYear   last year; omitted for the current year
     * @return 200 with the counts; 400 for invalid input; 502 when the BLS API fails
     */
    @PostMapping("/import/bls-laus")
    @Operation(summary = "Import BLS county employment", description = "Monthly labor force, employment and "
            + "unemployment rate, not seasonally adjusted. A month already stored is updated (revised), never "
            + "duplicated. At most 10 years per import. Without BLS_API_KEY the API allows 25 requests a day "
            + "(25 series each, 3 series per county).")
    @ApiResponse(responseCode = "200", description = "Imported")
    @ApiResponse(responseCode = "400", description = "Unknown state or county, invalid years")
    @ApiResponse(responseCode = "409", description = "Migration V50 not applied")
    @ApiResponse(responseCode = "502", description = "BLS API unavailable, daily limit reached or unreadable answer")
    public EconomicIndicatorImportResultDTO importBlsLaus(@RequestParam String state,
                                                          @RequestParam(required = false) String fips,
                                                          @RequestParam(required = false) Integer fromYear,
                                                          @RequestParam(required = false) Integer toYear,
                                                          Principal principal) {
        return importService.importBlsLaus(state, fips, fromYear, toYear, principal == null ? null : principal.getName());
    }

    /**
     * Imports the population and net migration from one Census vintage.
     *
     * @param state   state code or name, for example FL
     * @param vintage vintage year, for example 2025; omitted for the previous year
     * @return 200 with the counts; 400 for invalid input or a vintage not published; 502 when the download fails
     */
    @PostMapping("/import/census-population")
    @Operation(summary = "Import Census county population and migration", description = "Population on July 1 "
            + "and net, domestic and international migration (July 1 to June 30) of every year since 2020. A newer "
            + "vintage revises the years already stored; an older vintage never overwrites a newer one.")
    @ApiResponse(responseCode = "200", description = "Imported")
    @ApiResponse(responseCode = "400", description = "Unknown state, invalid vintage or vintage not published")
    @ApiResponse(responseCode = "409", description = "Migration V50 not applied")
    @ApiResponse(responseCode = "502", description = "Census file unavailable or not in the expected format")
    public EconomicIndicatorImportResultDTO importCensusPopulation(@RequestParam String state,
                                                                   @RequestParam(required = false) Integer vintage,
                                                                   Principal principal) {
        return importService.importCensusPopulation(state, vintage, principal == null ? null : principal.getName());
    }
}
