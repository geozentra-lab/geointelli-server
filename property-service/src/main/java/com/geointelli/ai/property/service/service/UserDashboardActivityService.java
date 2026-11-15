package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ActivityCardDTO;

/**
 * Recent-activity card of the user dashboard (SCRUM-101).
 *
 * <p>The project keeps no activity log, so the card is derived from the records the current user
 * owns: favorites, saved searches, alert rules, portfolios and portfolio holdings. Each record
 * yields one item at its {@code created_at} and, when it was modified afterwards, a second item at
 * its {@code updated_at}. Nothing is synthesized and other users' records are never read.
 */
public interface UserDashboardActivityService {

    /** Number of items returned when the caller does not pass a limit. */
    int DEFAULT_LIMIT = 5;

    /** Largest limit a caller may ask for. */
    int MAX_LIMIT = 20;

    /**
     * Builds the recent-activity card of the current user.
     *
     * @param limit maximum number of items in {@code recent}, between 1 and {@link #MAX_LIMIT}
     * @return the card: {@code scope CURRENT_USER}, {@code status OK}, the applied limit and the
     *         user's activity items ordered by {@code occurredAt}, newest first, at most {@code limit};
     *         {@code recent} is empty when the user owns no records
     * @throws IllegalArgumentException when {@code limit} is outside 1..{@link #MAX_LIMIT}
     *         (rendered as 400 by the global error handler)
     * @throws org.springframework.web.server.ResponseStatusException with status 401 when no
     *         bearer token identifies the current user
     */
    ActivityCardDTO getActivityCard(int limit);
}
