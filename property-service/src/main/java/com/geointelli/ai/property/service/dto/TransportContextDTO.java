package com.geointelli.ai.property.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Transport section of the property location intelligence (SCRUM-126 / SCRUM-53). Built from the stored
 * transportation analysis of the property, with normalized categories; nothing is recalculated.
 * A failure of the transport data source gives an UNAVAILABLE section instead of an error response.
 */
@Data
public class TransportContextDTO {
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_NO_COVERAGE = "NO_COVERAGE";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    public static final String DISTANCE_UNIT = "m";
    public static final String SOURCE = "GeoZentra transportation analysis (sales ML dataset)";

    @Schema(description = "Internal ID of the property", example = "42")
    private Long propertyId;

    @Schema(description = "AVAILABLE when transport data exists; NO_COVERAGE when the property has no transport "
            + "data (location not analysed); UNAVAILABLE when the transport data source failed", example = STATUS_AVAILABLE)
    private String status;

    @Schema(description = "Unit of every distance in this section", example = DISTANCE_UNIT)
    private String distanceUnit;

    @Schema(description = "Supported transport categories with a known distance; empty unless status is AVAILABLE")
    private List<TransportOptionDTO> transport;

    @Schema(description = "Number of bus stops within 500 m; 0 means none, null means unknown", example = "4")
    private Integer busStopsWithin500m;

    @Schema(description = "Stored transportation score (0-100); null if not calculated yet", example = "52.5")
    private BigDecimal transportationScore;

    @Schema(description = "Where the transport data comes from", example = SOURCE)
    private String source;

    @Schema(description = "Freshness: when the transportation analysis was last updated; null unless AVAILABLE")
    private LocalDateTime dataUpdatedAt;

    @Schema(description = "Why the transport data could not be read (UNAVAILABLE); null otherwise",
            example = "Transport data source unavailable")
    private String error;

    @Schema(description = "Explanation when data is missing or incomplete; null otherwise",
            example = "Unknown distances: PEOPLE_MOVER")
    private String note;
}
