package com.geointelli.ai.property.service.dto;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * School section of the property location intelligence (SCRUM-125 / SCRUM-53, SCRUM-203). Built from the stored
 * school data of the property; nothing is recalculated. The v2 fields (assigned schools and school score v2) come
 * from the attendance zones and FLDOE grades; the v1 fields (nearest distances, schools within 1 km and their score)
 * from the sales dataset of the ML model, kept for one release. Distinguishes a property without school data
 * (NO_COVERAGE) from a property with v1 data only and no school within 1 km (NO_NEARBY_RESULTS).
 */
@Data
public class SchoolContextDTO {

    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_NO_NEARBY_RESULTS = "NO_NEARBY_RESULTS";
    public static final String STATUS_NO_COVERAGE = "NO_COVERAGE";
    public static final String DISTANCE_UNIT = "m";
    public static final String SOURCE = "GeoZentra school analysis (sales ML dataset)";
    /** Source of the v2 fields; the FLDOE year is added in front when known. */
    public static final String SOURCE_V2 = "NCES CCD 2024-25 school locations and district attendance zones";
    public static final String LABEL_EXCELLENT = "Excellent";
    public static final String LABEL_GOOD = "Good";
    public static final String LABEL_FAIR = "Fair";
    public static final String LABEL_LIMITED = "Limited";

    @Schema(description = "Internal ID of the property", example = "42")
    private Long propertyId;

    @Schema(description = "AVAILABLE when school data exists (assigned schools, or v1 data with a school within "
            + "1 km); NO_NEARBY_RESULTS when only v1 data exists and no school is within 1 km; NO_COVERAGE when no "
            + "school data exists for the property (for v2: its county has no attendance zones loaded)",
            example = STATUS_AVAILABLE)
    private String status;

    @Schema(description = "Unit of every distance in this section", example = DISTANCE_UNIT)
    private String distanceUnit;

    @Schema(description = "Schools the property is assigned to, elementary, middle then high (v2); empty when the "
            + "property has no assignment")
    private List<AssignedSchoolDTO> assignedSchools = new ArrayList<>();

    @Schema(description = "School score v2 (0-100), rounded: 60% quality, 25% access, 15% choice, over the known "
            + "parts only; null when not computed", example = "72")
    private Integer schoolScoreV2;

    @Schema(description = "Excellent (80+), Good (60-79), Fair (40-59) or Limited (under 40) for schoolScoreV2",
            example = LABEL_GOOD)
    private String schoolScoreV2Label;

    @Schema(description = "Quality part of score v2 (0-100): FLDOE statewide percentile of the assigned schools, "
            + "elementary 40%, middle 30%, high 30%; null when none of them is graded", example = "78")
    private Integer qualityScore;

    @Schema(description = "Access part of score v2 (0-100): distance to the assigned schools, 100 within 1 mile, 75 "
            + "within 2, 50 within 5, 25 within 10, else 0", example = "75")
    private Integer accessScore;

    @Schema(description = "Choice part of score v2 (0-100): best statewide percentile among the other public and "
            + "charter schools within 3 miles; 0 when there is none, null when none of them is graded", example = "88")
    private Integer choiceScore;

    @Schema(description = "FLDOE school year (spring of the year) of the grades used by score v2", example = "2026")
    private Integer gradeYear;

    @Schema(description = "Version of the formula of schoolScoreV2", example = "2")
    private Integer scoreVersion;

    @Schema(description = "v1, deprecated: distance to the nearest public school (sales ML dataset); null if unknown",
            example = "800")
    private Double nearestPublicSchoolDistance;

    @Schema(description = "v1, deprecated: distance to the nearest charter school; null if unknown", example = "1800")
    private Double nearestCharterSchoolDistance;

    @Schema(description = "v1, deprecated: number of schools within 1 km; 0 means none nearby, null means unknown",
            example = "3")
    private Integer schoolsWithin1km;

    @Schema(description = "v1, deprecated: school score (0-100), rounded, over the known values only; null if the "
            + "score is not calculated yet", example = "55")
    private Integer schoolScore;

    @Schema(description = "v1, deprecated: Excellent (80+), Good (60-79), Fair (40-59) or Limited (under 40); null "
            + "when there is no score", example = LABEL_FAIR)
    private String schoolScoreLabel;

    @Schema(description = "v1, deprecated: public school distance part of the score (0-100, 40% weight); null if "
            + "unknown", example = "75")
    private Integer publicSchoolDistanceScore;

    @Schema(description = "v1, deprecated: charter school distance part of the score (0-100, 20% weight); null if "
            + "unknown", example = "25")
    private Integer charterSchoolDistanceScore;

    @Schema(description = "v1, deprecated: schools within 1 km part of the score (0-100, 40% weight); null if unknown",
            example = "50")
    private Integer schoolDensityScore;

    @Schema(description = "Where the school data comes from: the FLDOE grades, school locations and attendance zones "
            + "when the property has assigned schools, else the v1 sales dataset",
            example = "FLDOE School Grades 2026, " + SOURCE_V2)
    private String source;

    @Schema(description = "Explanation when data is missing or incomplete; null otherwise",
            example = "Score based on known values only, unknown values: nearestCharterSchoolDistance")
    private String note;
}
