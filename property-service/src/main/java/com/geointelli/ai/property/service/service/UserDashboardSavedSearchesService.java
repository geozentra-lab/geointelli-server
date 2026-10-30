package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.SavedSearchesCardDTO;

/**
 * Saved-search card of the normal-user dashboard (SCRUM-96). Reads the stored saved-search
 * definitions of the current user only; it never executes a saved search and never sends a
 * notification.
 */
public interface UserDashboardSavedSearchesService {
    /** Default number of recent saved searches when the caller does not specify one. */
    int DEFAULT_LIMIT = 5;
    /** Largest number of recent saved searches a caller may ask for. */
    int MAX_LIMIT = 20;

    /**
     * Builds the saved-search card for the user identified by the bearer token.
     * <p>
     * {@code count} is the user's real total; {@code recent} holds at most {@code limit} saved
     * searches, newest first, each with its monitoring state (cadence and paused). A user without
     * saved searches gets {@code count 0} and an empty list. Records of other users are never read.
     *
     * @param limit maximum number of recent saved searches, 1 to {@value #MAX_LIMIT}
     * @return the card with {@code status OK}
     * @throws IllegalArgumentException if {@code limit} is outside 1 to {@value #MAX_LIMIT}
     * @throws org.springframework.web.server.ResponseStatusException 401 when no bearer token
     *         identifies the current user
     */
    SavedSearchesCardDTO getSavedSearchesCard(int limit);
}
