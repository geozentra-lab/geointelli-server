package com.geointelli.ai.property.service.dto;

import java.util.List;

import lombok.Data;

/**
 * School analysis of a property. The v1 fields (distances, schools within 1 km and their score) come from
 * property_school_analysis, the sales dataset of the ML model, and are kept for one release; the v2 fields (assigned
 * schools and school score v2, SCRUM-203) come from property_school_assignments and property_school_scores. Either
 * group is null when the property has no such data.
 */
@Data
public class SchoolAnalysisDTO {
    private Long id;

    private Long propertyId;

    private Double distPublicSchool;

    private Double distCharterSchool;

    private Integer schoolsWithin1km;

    private Double publicSchoolDistanceScore;

    private Double charterSchoolDistanceScore;

    private Double schoolDensityScore;

    private Double schoolScore;

    /** Schools the property is assigned to, elementary, middle then high (v2). */
    private List<AssignedSchoolDTO> assignedSchools;

    /** FLDOE statewide percentile of the assigned schools, 0-100 (v2, 60% of the score). */
    private Double qualityScore;

    /** Distance to the assigned schools, 0-100 (v2, 25%). */
    private Double accessScore;

    /** Best graded school within 3 miles other than the assigned one, 0-100 (v2, 15%). */
    private Double choiceScore;

    /** School score v2, 0-100, over the known parts only. */
    private Double schoolScoreV2;

    /** Excellent, Good, Fair or Limited, from the rounded school score v2. */
    private String schoolScoreV2Label;

    /** FLDOE school year the v2 quality and choice parts use. */
    private Integer gradeYear;

    /** Version of the v2 formula that computed the score. */
    private Integer scoreVersion;
}
