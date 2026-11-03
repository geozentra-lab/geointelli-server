package com.geointelli.ai.property.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.geointelli.ai.property.service.entity.SavedSearchCadence;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Saved-search card of the user dashboard (SCRUM-96): the current user's saved-search count and
 * their newest saved searches with monitoring state. Built only from the stored definitions;
 * no saved search is executed to produce it.
 */
@Data
public class SavedSearchesCardDTO {
    /** Value of {@link #scope} for every dashboard card that is read as the current user. */
    public static final String SCOPE_CURRENT_USER = "CURRENT_USER";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";

    @Schema(description = "Whose data the card shows; always CURRENT_USER", example = SCOPE_CURRENT_USER)
    private String scope;

    @Schema(description = "Freshness: when the card was built (server time)")
    private LocalDateTime generatedAt;

    @Schema(description = "OK when the data loaded; ERROR when the dashboard could not load this card", example = STATUS_OK)
    private String status;

    @Schema(description = "Why the card failed; null when status is OK")
    private String error;

    @Schema(description = "Total number of saved searches of the current user, not just the ones listed", example = "3")
    private long count;

    @Schema(description = "Maximum number of items in recent", example = "5")
    private int limit;

    @Schema(description = "Newest saved searches first, at most limit items; empty when the user has none")
    private List<RecentSavedSearch> recent;

    /**
     * One saved search, summarized for display: identity, name and monitoring state. The stored
     * filters are not included; they are available from {@code GET /api/saved-searches/{id}}.
     */
    public record RecentSavedSearch(
        Long id,
        String name,
        @Schema(description = "Requested monitoring frequency (DAILY or WEEKLY)") SavedSearchCadence cadence,
        @Schema(description = "true when monitoring is paused; manual replay stays available") boolean paused,
        @Schema(description = "When the search was saved") LocalDateTime createdAt,
        @Schema(description = "When the search was last renamed, edited, paused or resumed") LocalDateTime updatedAt) {
    }
}
