package com.geointelli.ai.property.service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.ActivityCardDTO;
import com.geointelli.ai.property.service.service.UserDashboardActivityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Recent-activity card of the normal-user dashboard (SCRUM-84 / SCRUM-101). Each dashboard card has
 * its own endpoint under {@code /api/dashboards/user} so it can be verified alone; the composed
 * {@code GET /api/dashboards/user} is added by SCRUM-102.
 */
@RestController
@RequestMapping("/api/dashboards/user")
@RequiredArgsConstructor
@Tag(name = "User dashboard", description = "Bearer token identifies the user; every card shows that user's data only.")
@SecurityRequirement(name = "bearerAuth")
public class UserDashboardActivityController {
    private final UserDashboardActivityService activityService;

    /**
     * Recent-activity card: the newest things the current user did, derived from the records they
     * own (favorites, saved searches, alert rules, portfolios, holdings) and their stored timestamps.
     *
     * @param limit maximum number of items, 1 to {@value UserDashboardActivityService#MAX_LIMIT},
     *              default {@value UserDashboardActivityService#DEFAULT_LIMIT}
     * @return 200 with the card; 400 if {@code limit} is out of range or not a number; 401 without a bearer token
     */
    @GetMapping("/activity")
    @Operation(summary = "Recent-activity card of my dashboard", description = "The newest things I did (default 5, "
        + "max 20), newest first: favorites added, saved searches and alert rules created or modified, portfolios "
        + "created or modified, holdings added or modified. Every item is a real stored record with its stored "
        + "created_at or updated_at; the project keeps no activity log, so deletions, logins and views are not "
        + "shown. A user without records gets an empty list. Owner is resolved from the bearer token; other users "
        + "are never read.")
    public ResponseEntity<ActivityCardDTO> activity(
            @RequestParam(defaultValue = "" + UserDashboardActivityService.DEFAULT_LIMIT) int limit) {
        return ResponseEntity.ok(activityService.getActivityCard(limit));
    }
}
