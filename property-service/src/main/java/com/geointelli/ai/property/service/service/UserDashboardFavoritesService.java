package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.FavoritesCardDTO;

/**
 * Favorites card of the normal-user dashboard (SCRUM-95). Reads the existing favorite records of
 * the current user only; it never values a property or triggers a notification.
 */
public interface UserDashboardFavoritesService {
    /** Default number of recent favorites when the caller does not specify one. */
    int DEFAULT_LIMIT = 5;
    /** Largest number of recent favorites a caller may ask for. */
    int MAX_LIMIT = 20;

    /**
     * Builds the favorites card for the user identified by the bearer token.
     * <p>
     * {@code count} is the user's real total; {@code recent} holds at most {@code limit} favorites,
     * newest first. A user without favorites gets {@code count 0} and an empty list. Records of
     * other users are never read.
     *
     * @param limit maximum number of recent favorites, 1 to {@value #MAX_LIMIT}
     * @return the card with {@code status OK}
     * @throws IllegalArgumentException if {@code limit} is outside 1 to {@value #MAX_LIMIT}
     * @throws org.springframework.web.server.ResponseStatusException 401 when no bearer token
     *         identifies the current user
     */
    FavoritesCardDTO getFavoritesCard(int limit);
}
