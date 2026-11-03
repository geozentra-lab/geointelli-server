package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.AlertsCardDTO;

/**
 * Alerts card of the normal-user dashboard (SCRUM-97). Reads the configured alert rules of the
 * current user only (SCRUM-67 storage); it never evaluates a rule, never reads or counts delivered
 * notifications, and never sends anything.
 */
public interface UserDashboardAlertsService {
    /** Default number of recent rules when the caller does not specify one. */
    int DEFAULT_LIMIT = 5;
    /** Largest number of recent rules a caller may ask for. */
    int MAX_LIMIT = 20;

    /**
     * Builds the alerts card for the user identified by the bearer token.
     * <p>
     * {@code count} is the user's real number of rules, split into {@code activeCount} and
     * {@code pausedCount} by effective state (a rule is paused when its own flag is set or when its
     * linked saved search is paused). {@code recent} holds at most {@code limit} rules, newest
     * first. A user without rules gets zero counts and an empty list. Rules of other users are
     * never read.
     *
     * @param limit maximum number of recent rules, 1 to {@value #MAX_LIMIT}
     * @return the card with {@code status OK}
     * @throws IllegalArgumentException if {@code limit} is outside 1 to {@value #MAX_LIMIT}
     * @throws org.springframework.web.server.ResponseStatusException 401 when no bearer token
     *         identifies the current user
     */
    AlertsCardDTO getAlertsCard(int limit);
}
