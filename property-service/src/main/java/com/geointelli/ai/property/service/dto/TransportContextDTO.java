package com.geointelli.ai.property.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Transport section of the property location intelligence (SCRUM-126 / SCRUM-53). Built from the stored
 * transportation analysis of the property, with normalized categories; nothing is saved.
 * A failure of the transport data source gives an UNAVAILABLE section instead of an error response.
 */
@Data
public class TransportContextDTO {
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_NO_COVERAGE = "NO_COVERAGE";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    public static final String DISTANCE_UNIT = "m";
    public static final String SOURCE = "GeoZentra transportation analysis (sales ML dataset)";
    public static final String LABEL_EXCELLENT = "Excellent";
    public static final String LABEL_GOOD = "Good";
    public static final String LABEL_FAIR = "Fair";
    public static final String LABEL_LIMITED = "Limited";

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

    @Schema(description = "Transportation score (0-100), rounded, over the known values only; null if the score "
            + "is not calculated yet", example = "53")
    private Integer transportationScore;

    @Schema(description = "Excellent (80+), Good (60-79), Fair (40-59) or Limited (under 40); null when there is "
            + "no score", example = LABEL_FAIR)
    private String transportationScoreLabel;

    @Schema(description = "Where the transport data comes from", example = SOURCE)
    private String source;

    @Schema(description = "Why the transport data could not be read (UNAVAILABLE); null otherwise",
            example = "Transport data source unavailable")
    private String error;

    @Schema(description = "Explanation when data is missing or incomplete; null otherwise",
            example = "Score based on known values only, unknown values: PEOPLE_MOVER")
    private String note;
}
