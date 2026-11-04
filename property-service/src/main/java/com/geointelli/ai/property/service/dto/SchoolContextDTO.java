package com.geointelli.ai.property.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * School section of the property location intelligence (SCRUM-125 / SCRUM-53). Built from the stored
 * school analysis of the property; nothing is recalculated. Distinguishes a property without school
 * data (NO_COVERAGE) from a property with data but no school within 1 km (NO_NEARBY_RESULTS).
 */
@Data
public class SchoolContextDTO {

    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_NO_NEARBY_RESULTS = "NO_NEARBY_RESULTS";
    public static final String STATUS_NO_COVERAGE = "NO_COVERAGE";
    public static final String DISTANCE_UNIT = "m";
    public static final String SOURCE = "GeoZentra school analysis (sales ML dataset)";
    public static final String LABEL_EXCELLENT = "Excellent";
    public static final String LABEL_GOOD = "Good";
    public static final String LABEL_FAIR = "Fair";
    public static final String LABEL_LIMITED = "Limited";

    @Schema(description = "Internal ID of the property", example = "42")
    private Long propertyId;

    @Schema(description = "AVAILABLE when school data exists; NO_NEARBY_RESULTS when data exists but no school "
            + "is within 1 km; NO_COVERAGE when no school data exists for the property", example = STATUS_AVAILABLE)
    private String status;

    @Schema(description = "Unit of every distance in this section", example = DISTANCE_UNIT)
    private String distanceUnit;

    @Schema(description = "Distance to the nearest public school; null if unknown", example = "800")
    private Double nearestPublicSchoolDistance;

    @Schema(description = "Distance to the nearest charter school; null if unknown", example = "1800")
    private Double nearestCharterSchoolDistance;

    @Schema(description = "Number of schools within 1 km; 0 means none nearby, null means unknown", example = "3")
    private Integer schoolsWithin1km;

    @Schema(description = "School score (0-100), rounded, over the known values only; null if the score is not "
            + "calculated yet", example = "55")
    private Integer schoolScore;

    @Schema(description = "Excellent (80+), Good (60-79), Fair (40-59) or Limited (under 40); null when there is "
            + "no score", example = LABEL_FAIR)
    private String schoolScoreLabel;

    @Schema(description = "Public school distance part of the score (0-100, 40% weight); null if unknown", example = "75")
    private Integer publicSchoolDistanceScore;

    @Schema(description = "Charter school distance part of the score (0-100, 20% weight); null if unknown", example = "25")
    private Integer charterSchoolDistanceScore;

    @Schema(description = "Schools within 1 km part of the score (0-100, 40% weight); null if unknown", example = "50")
    private Integer schoolDensityScore;

    @Schema(description = "Where the school data comes from", example = SOURCE)
    private String source;

    @Schema(description = "Explanation when data is missing or incomplete; null otherwise",
            example = "Score based on known values only, unknown values: nearestCharterSchoolDistance")
    private String note;
}
