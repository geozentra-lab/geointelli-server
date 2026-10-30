package com.geointelli.ai.property.service.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.PortfolioSummaryDTO;
import com.geointelli.ai.property.service.service.PortfolioSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Reads the authenticated owner's portfolio performance without running projections or changing source data. */
@RestController
@RequestMapping("/api/portfolios")
@Tag(name="Portfolios")
@SecurityRequirement(name="bearerAuth")
public class PortfolioSummaryController {
    private final PortfolioSummaryService summaries;

    public PortfolioSummaryController(PortfolioSummaryService summaries) { this.summaries=summaries; }
    
    @GetMapping("/{id}/summary")
    @Operation(summary="Summarize my portfolio performance",description="Owner-scoped; missing or foreign portfolios return 404. "
        + "Financial period is inclusive by transactionDate, without prorating coverage dates. toDate defaults to today UTC; "
        + "fromDate defaults to January 1 of toDate's year. Future toDate and reversed ranges return 400. "
        + "Financing, geography and occupancy describe CURRENT holdings as of today UTC, even for historical cash periods. "
        + "Full-property USD amounts are multiplied by CURRENT ownership share exactly once; no historical ownership is inferred. "
        + "Cash flow is recorded income minus recorded expenses, not profit, ROI or projected rent. "
        + "Market values remain unavailable until SCRUM-58 provides supported valuations; purchase prices and legacy predictions are not substitutes. "
        + "Incomplete metrics have total:null and a labelled knownTotal/coverage. Empty portfolios return zero counts, empty lists and null rental occupancy. "
        + "Component sections are read independently, not as one atomic portfolio snapshot. AVAILABLE means sections loaded, not complete source coverage. "
        + "A failed section is null with a stable errors entry and status PARTIAL; all sections failing returns 503. "
        + "recordsUpdatedAt/holdingsUpdatedAt are source-local audit timestamps, not guarantees that an owner's records are complete.")
    @ApiResponse(responseCode="200",description="Summary with explicit coverage and any partial errors")
    @ApiResponse(responseCode="400",description="Invalid portfolio ID or reporting dates")
    @ApiResponse(responseCode="401",description="Bearer token required")
    @ApiResponse(responseCode="404",description="Portfolio missing or owned by another user")
    @ApiResponse(responseCode="503",description="All summary sections unavailable")
    public PortfolioSummaryDTO summarize(@PathVariable Long id,
            @Parameter(description="First included cash transaction date",schema=@Schema(type="string",format="date"))
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description="Last included cash transaction date, not a historical holdings snapshot",schema=@Schema(type="string",format="date"))
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return summaries.summarize(id,fromDate,toDate);
    }
}
