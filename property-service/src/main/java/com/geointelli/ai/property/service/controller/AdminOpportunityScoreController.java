package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.OpportunityRefreshResultDTO;
import com.geointelli.ai.property.service.service.OpportunityScoreRefreshService;
import com.geointelli.ai.property.service.service.OpportunityScoreRefreshService.Mode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration of the stored opportunity scores (SCRUM-76). {@code /api/admin/**} requires the admin role.
 */
@RestController
@RequestMapping("/api/admin/opportunity-scores")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin opportunity scores", description = "Recalculate the stored opportunity scores read by "
        + "GET /api/opportunities. Admin role required.")
public class AdminOpportunityScoreController {

    private final OpportunityScoreRefreshService refreshService;

    /**
     * Recalculates stored scores now and waits for the run to finish.
     *
     * @param mode INCREMENTAL (missing or stale scores, default) or FULL (every property with a valuation)
     * @return 200 with the run counts; 400 for an unknown mode; 409 if a run is already in progress
     */
    @PostMapping("/refresh")
    @Operation(summary = "Recalculate opportunity scores", description = "INCREMENTAL scores properties whose stored "
            + "score is missing, from an older scoring version or from an older valuation run; FULL rescores every "
            + "property with a valuation. Scores of properties without a valuation are removed. Synchronous.")
    public OpportunityRefreshResultDTO refresh(@RequestParam(defaultValue = "INCREMENTAL") Mode mode) {
        return refreshService.refresh(mode);
    }
}
