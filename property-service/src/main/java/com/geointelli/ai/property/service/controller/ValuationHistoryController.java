package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.ValuationHistoryEntry;
import com.geointelli.ai.property.service.service.ValuationHistoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

/** Read-only, frozen valuation history (SCRUM-142). */
@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class ValuationHistoryController {
    private final ValuationHistoryService historyService;

    /**
     * Reads stored model versions without running a valuation or fetching live comparables.
     *
     * @param id positive property ID
     * @param page zero-based page, default zero
     * @param size page size, default twenty and maximum one hundred
     * @return stored versions; an existing property with no recorded versions has an empty page
     */
    @GetMapping("/id/{id}/valuations")
    @Operation(summary = "Read valuation history", description = "Returns archived "
        + "versions newest first with original method/model dates, run/input references and a separate archive time. "
        + "History uses frozen model and sale evidence; missing archived comparables stay NOT_RECORDED. "
        + "No current records or forecasts are substituted for historical values. modelEvidence contains frozen "
        + "last-sale and nearby-sales inputs, not current comparable listings. Seeded versions are labelled recorded "
        + "before history. method.createdAt is null: use loadedAt, scoredAt and archivedAt (UTC Instants). "
        + "This endpoint never triggers a refresh.")
    @ApiResponse(responseCode = "200", description = "A page of stored versions, possibly empty")
    @ApiResponse(responseCode = "400", description = "Invalid ID or pagination")
    @ApiResponse(responseCode = "404", description = "Property does not exist")
    public PageResponse<ValuationHistoryEntry> getHistory(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return historyService.getHistory(id, page, size);
    }
}
