package com.geointelli.ai.property.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "property_school_analysis",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_property_school_analysis_property",
                          columnNames = "property_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SchoolAnalysis extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;
    
    @Column(name = "dist_public_school")
    private Double distPublicSchool;

    @Column(name = "dist_charter_school")
    private Double distCharterSchool;

    @Column(name = "schools_within_1km")
    private Integer schoolsWithin1km;

    @Column(name = "public_school_distance_score")
    private Double publicSchoolDistanceScore;

    @Column(name = "charter_school_distance_score")
    private Double charterSchoolDistanceScore;

    @Column(name = "school_density_score")
    private Double schoolDensityScore;

    @Column(name = "school_score")
    private Double schoolScore;
}
