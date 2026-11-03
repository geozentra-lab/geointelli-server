package com.geointelli.ai.property.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

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

    private LocalDateTime updatedAt;
}
