package com.geointelli.ai.property.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * One school a property is assigned to (SCRUM-203): the school of the attendance zone containing the property, or
 * the nearest zoned public school when no zone does, with its FLDOE grade. A school without a grade has every grade
 * field null and a note saying it is not graded; this never means a low rating. When the zone's school is not in the
 * school data (UNMATCHED_ZONE), only the name the zone gives is known: the level is left out of the score instead of
 * using another school.
 */
@Data
public class AssignedSchoolDTO {

    public static final String METHOD_ZONE = "ZONE";
    public static final String METHOD_UNMATCHED_ZONE = "UNMATCHED_ZONE";
    public static final String METHOD_NEAREST = "NEAREST";

    @Schema(description = "ELEMENTARY, MIDDLE or HIGH", example = "ELEMENTARY")
    private String level;

    @Schema(description = "Internal ID of the school; null for UNMATCHED_ZONE", example = "812")
    private Long schoolId;

    @Schema(description = "School name; for UNMATCHED_ZONE, the name the attendance zone gives",
            example = "CORAL WAY K-8 CENTER")
    private String name;

    @Schema(description = "PUBLIC or CHARTER", example = "PUBLIC")
    private String sector;

    @Schema(description = "FLDOE letter grade (A-F) of gradeYear; null when FLDOE gave none", example = "A")
    private String letterGrade;

    @Schema(description = "FLDOE grade code as published: A-F, or I (incomplete: too few students tested); null "
            + "when the school is not listed for gradeYear", example = "A")
    private String fldoeGrade;

    @Schema(description = "Percent of the FLDOE points earned (0-100); null when not graded", example = "69")
    private Double pctTotalPoints;

    @Schema(description = "Rank of pctTotalPoints among Florida schools of the same FLDOE type and year (0-100); "
            + "null when not graded", example = "83")
    private Integer statewidePercentile;

    @Schema(description = "FLDOE school year (spring of the year) of the grade fields", example = "2026")
    private Integer gradeYear;

    @Schema(description = "Distance from the property to the school in metres; null for UNMATCHED_ZONE",
            example = "640.2")
    private Double distanceMeters;

    @Schema(description = "ZONE: the property is in the school's attendance zone; UNMATCHED_ZONE: the property is in "
            + "an attendance zone whose school is not in the school data, so the level has no school and is left out "
            + "of the score; NEAREST: no zone covers the property, so the nearest zoned public school of the county "
            + "is shown", example = METHOD_ZONE)
    private String assignmentMethod;

    @Schema(description = "How far the zone can be trusted, best first: OFFICIAL, OFFICIAL_RECONSTRUCTED, "
            + "COUNTY_WIDE_SINGLE_SCHOOL, NCES_SABS_2015_16, UNOFFICIAL; null for NEAREST", example = "OFFICIAL")
    private String zoneSourceType;

    @Schema(description = "Why a value is missing or how to read it; null otherwise",
            example = "Not graded by FLDOE for 2026")
    private String note;
}
