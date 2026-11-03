package com.geointelli.ai.property.service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.SavedSearchesCardDTO;
import com.geointelli.ai.property.service.service.UserDashboardSavedSearchesService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Saved-search card of the normal-user dashboard (SCRUM-84 / SCRUM-96). Each dashboard card has
 * its own endpoint under {@code /api/dashboards/user} so it can be verified alone; the composed
 * {@code GET /api/dashboards/user} is added by SCRUM-102.
 */
@RestController
@RequestMapping("/api/dashboards/user")
@RequiredArgsConstructor
@Tag(name = "User dashboard", description = "Bearer token identifies the user; every card shows that user's data only.")
@SecurityRequirement(name = "bearerAuth")
public class UserDashboardSavedSearchesController {
    private final UserDashboardSavedSearchesService savedSearchesService;

    /**
     * Saved-search card: the current user's saved-search count and newest saved searches with
     * their monitoring state.
     *
     * @param limit maximum number of recent saved searches, 1 to
     *              {@value UserDashboardSavedSearchesService#MAX_LIMIT}, default
     *              {@value UserDashboardSavedSearchesService#DEFAULT_LIMIT}
     * @return 200 with the card; 400 if {@code limit} is out of range or not a number; 401 without a bearer token
     */
    @GetMapping("/saved-searches")
    @Operation(summary = "Saved-search card of my dashboard", description = "Real saved-search count plus the newest "
        + "saved searches (default 5, max 20), newest first, each with cadence and paused state. No saved search is "
        + "executed. A user without saved searches gets count 0 and an empty list. Owner is resolved from the bearer "
        + "token; other users' saved searches are never read.")
    public ResponseEntity<SavedSearchesCardDTO> savedSearches(
            @RequestParam(defaultValue = "" + UserDashboardSavedSearchesService.DEFAULT_LIMIT) int limit) {
        return ResponseEntity.ok(savedSearchesService.getSavedSearchesCard(limit));
    }
}
