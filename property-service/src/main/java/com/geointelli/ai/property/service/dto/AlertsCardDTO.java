package com.geointelli.ai.property.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.geointelli.ai.property.service.entity.AlertCadence;
import com.geointelli.ai.property.service.entity.AlertEventType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Alerts card of the user dashboard (SCRUM-97): the current user's configured alert rules, counted
 * by effective active/paused state, plus their newest rules. Built only from alert rules; delivered
 * notifications (alert outbox) are a different thing and are never counted here.
 */
@Data
public class AlertsCardDTO {
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

    @Schema(description = "Total number of alert rules of the current user (activeCount + pausedCount)", example = "3")
    private long count;

    @Schema(description = "Rules that will be evaluated: neither paused themselves nor linked to a paused saved search", example = "2")
    private long activeCount;

    @Schema(description = "Rules that are effectively paused: paused themselves or linked to a paused saved search", example = "1")
    private long pausedCount;

    @Schema(description = "Maximum number of items in recent", example = "5")
    private int limit;

    @Schema(description = "Newest rules first, at most limit items; empty when the user has none")
    private List<RecentAlertRule> recent;

    /** One configured rule, summarized for display: what it watches, its trigger and its pause state. */
    public record RecentAlertRule(
        Long id,
        String name,
        @Schema(description = "NEW_MATCH, LISTING_PRICE_CHANGED or VALUATION_CHANGED") AlertEventType eventType,
        @Schema(description = "Watched property, or null when the rule watches a saved search") Long propertyId,
        @Schema(description = "Watched saved search, or null when the rule watches a property") Long savedSearchId,
        @Schema(description = "Requested evaluation frequency (DAILY or WEEKLY)") AlertCadence cadence,
        @Schema(description = "The rule's own pause flag") boolean paused,
        @Schema(description = "paused OR the linked saved search is paused; the state used for the counts") boolean effectivePaused,
        @Schema(description = "When the rule was configured") LocalDateTime createdAt,
        @Schema(description = "Last rename, reschedule, pause or resume") LocalDateTime updatedAt) {
    }
}
