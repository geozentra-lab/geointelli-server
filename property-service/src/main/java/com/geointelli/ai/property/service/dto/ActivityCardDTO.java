package com.geointelli.ai.property.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Recent-activity card of the user dashboard (SCRUM-101): the newest things the current user did,
 * derived from the timestamps of the records they own (favorites, saved searches, alert rules,
 * portfolios and holdings). The project has no activity log, so every item corresponds to one real
 * stored row and its real {@code created_at} or {@code updated_at}; nothing is synthesized and
 * actions that leave no row behind (deletions, logins, views) cannot appear.
 */
@Data
public class ActivityCardDTO {
    /** Value of {@link #scope} for every dashboard card that is read as the current user. */
    public static final String SCOPE_CURRENT_USER = "CURRENT_USER";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";

    @Schema(description = "Whose data the card shows; always CURRENT_USER", example = SCOPE_CURRENT_USER)
    private String scope;

    @Schema(description = "Server time at which the card was built")
    private LocalDateTime generatedAt;

    @Schema(description = "OK when the data loaded; ERROR is reserved for the composed dashboard", example = STATUS_OK)
    private String status;

    @Schema(description = "Reason when status is ERROR, otherwise null", nullable = true)
    private String error;

    @Schema(description = "Maximum number of items in recent (the bound that was applied)", example = "5")
    private int limit;

    @Schema(description = "Newest activity first, at most limit items; empty when the user has no records")
    private List<ActivityItem> recent;

    /** Kind of activity an item represents; one value per record type and per stored timestamp. */
    public enum ActivityType {
        FAVORITE_ADDED,
        SAVED_SEARCH_CREATED,
        SAVED_SEARCH_UPDATED,
        ALERT_RULE_CREATED,
        ALERT_RULE_UPDATED,
        PORTFOLIO_CREATED,
        PORTFOLIO_UPDATED,
        HOLDING_ADDED,
        HOLDING_UPDATED
    }

    /** One line of the card: a real stored record and the real time it was created or last modified. */
    public record ActivityItem(
        @Schema(description = "What happened", example = "FAVORITE_ADDED")
        ActivityType type,
        @Schema(description = "The stored created_at or updated_at of the record")
        LocalDateTime occurredAt,
        @Schema(description = "Id of the record (favorite, saved search, alert rule, portfolio or holding)", example = "12")
        Long entityId,
        @Schema(description = "Readable summary: the search, rule or portfolio name; the property id for favorites and holdings",
            example = "Miami homes")
        String label,
        @Schema(description = "Property involved, or null when the item is not about a property", nullable = true, example = "4")
        Long propertyId,
        @Schema(description = "Portfolio the holding belongs to; null for every other type", nullable = true, example = "3")
        Long portfolioId) {
    }
}
