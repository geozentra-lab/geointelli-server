package com.geointelli.ai.property.service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.AlertsCardDTO;
import com.geointelli.ai.property.service.service.UserDashboardAlertsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Alerts card of the normal-user dashboard (SCRUM-84 / SCRUM-97). Each dashboard card has its own
 * endpoint under {@code /api/dashboards/user} so it can be verified alone; the composed
 * {@code GET /api/dashboards/user} is added by SCRUM-102.
 */
@RestController
@RequestMapping("/api/dashboards/user")
@RequiredArgsConstructor
@Tag(name = "User dashboard", description = "Bearer token identifies the user; every card shows that user's data only.")
@SecurityRequirement(name = "bearerAuth")
public class UserDashboardAlertsController {
    private final UserDashboardAlertsService alertsService;

    /**
     * Alerts card: the current user's alert rules counted by effective active/paused state, plus
     * their newest rules. Delivered notifications are not counted.
     *
     * @param limit maximum number of recent rules, 1 to
     *              {@value UserDashboardAlertsService#MAX_LIMIT}, default
     *              {@value UserDashboardAlertsService#DEFAULT_LIMIT}
     * @return 200 with the card; 400 if {@code limit} is out of range or not a number; 401 without a bearer token
     */
    @GetMapping("/alerts")
    @Operation(summary = "Alerts card of my dashboard", description = "Real number of configured alert rules, split "
        + "into activeCount and pausedCount by effective state (a rule linked to a paused saved search counts as "
        + "paused), plus the newest rules (default 5, max 20), newest first. Rules only: delivered notifications are "
        + "never counted. A user without rules gets zero counts and an empty list. Owner is resolved from the bearer "
        + "token; other users' rules are never read.")
    public ResponseEntity<AlertsCardDTO> alerts(
            @RequestParam(defaultValue = "" + UserDashboardAlertsService.DEFAULT_LIMIT) int limit) {
        return ResponseEntity.ok(alertsService.getAlertsCard(limit));
    }
}
