package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.InvestorOccupancyExposureCardDTO;
import com.geointelli.ai.property.service.service.InvestorOccupancyExposureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Investor dashboard (SCRUM-85). Each card has its own endpoint so it can be verified alone;
 * the composed {@code GET /api/dashboards/investor} is added by SCRUM-107.
 */
@RestController
@RequestMapping("/api/dashboards/investor")
@RequiredArgsConstructor
@Tag(name = "Investor dashboard", description = "Bearer token identifies the investor; every card reads only that "
        + "user's portfolios. Without portfolioId a card covers all of them; a foreign portfolioId returns 404.")
@SecurityRequirement(name = "bearerAuth")
public class InvestorDashboardController {

    private final InvestorOccupancyExposureService investorOccupancyExposureService;

    /**
     * Occupancy and exposure card: occupancy counts, rental occupancy, state and county exposure and their coverage.
     *
     * @param portfolioId optional portfolio of the current user; all portfolios when omitted
     * @return 200 with the card (EMPTY without holdings); 400 if portfolioId is invalid; 401 without a bearer token;
     *         404 if the portfolio is missing or belongs to another user
     */
    @GetMapping("/occupancy-exposure")
    @Operation(summary = "Occupancy and exposure card of my investor dashboard", description = "Adds up the portfolio "
            + "exposure summaries (SCRUM-74) of all my portfolios, or of portfolioId. Owner-reported current "
            + "occupancy with UNKNOWN kept as a category; rental occupancy = RENTED / (RENTED + VACANT). Exposure by "
            + "state and county counts holdings, unknown geography included. Coverage = share of holdings whose "
            + "occupancy or state is known. Without holdings: status EMPTY, zero counts, empty lists, null percentages.")
    public ResponseEntity<InvestorOccupancyExposureCardDTO> occupancyExposure(
            @RequestParam(required = false) Long portfolioId) {
        return ResponseEntity.ok(investorOccupancyExposureService.getCard(portfolioId));
    }
}
