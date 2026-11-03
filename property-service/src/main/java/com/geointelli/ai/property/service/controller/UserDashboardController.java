package com.geointelli.ai.property.service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.FavoritesCardDTO;
import com.geointelli.ai.property.service.service.UserDashboardFavoritesService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Normal-user dashboard (SCRUM-84). Each card has its own endpoint so it can be verified alone;
 * the composed {@code GET /api/dashboards/user} is added by SCRUM-102.
 */
@RestController
@RequestMapping("/api/dashboards/user")
@RequiredArgsConstructor
@Tag(name = "User dashboard", description = "Bearer token identifies the user; every card shows that user's data only.")
@SecurityRequirement(name = "bearerAuth")
public class UserDashboardController {
    private final UserDashboardFavoritesService favoritesService;

    /**
     * Favorites card: the current user's favorite count and newest favorites.
     *
     * @param limit maximum number of recent favorites, 1 to
     *              {@value UserDashboardFavoritesService#MAX_LIMIT}, default
     *              {@value UserDashboardFavoritesService#DEFAULT_LIMIT}
     * @return 200 with the card; 400 if {@code limit} is out of range or not a number; 401 without a bearer token
     */
    @GetMapping("/favorites")
    @Operation(summary = "Favorites card of my dashboard", description = "Real favorite count plus the newest favorites "
        + "(default 5, max 20), newest first. A user without favorites gets count 0 and an empty list. "
        + "Owner is resolved from the bearer token; other users' favorites are never read.")
    public ResponseEntity<FavoritesCardDTO> favorites(
            @RequestParam(defaultValue = "" + UserDashboardFavoritesService.DEFAULT_LIMIT) int limit) {
        return ResponseEntity.ok(favoritesService.getFavoritesCard(limit));
    }
}
