package com.geointelli.ai.property.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Favorites card of the user dashboard: the current user's favorite count and their newest
 * favorites. Built only from existing favorite records; nothing is valued or computed.
 */
@Data
public class FavoritesCardDTO {
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

    @Schema(description = "Total number of favorites of the current user, not just the ones listed", example = "12")
    private long count;

    @Schema(description = "Maximum number of items in recent", example = "5")
    private int limit;

    @Schema(description = "Newest favorites first, at most limit items; empty when the user has none")
    private List<RecentFavorite> recent;

    /** One favorited property, identified for display and navigation only. */
    public record RecentFavorite(
        Long propertyId,
        String folio,
        String address,
        String countyName,
        String stateCode,
        @Schema(description = "When the user favorited the property") LocalDateTime favoritedAt) {
    }
}
