package com.geointelli.ai.property.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "property_transportation_analysis",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_property_transportation_analysis_property",
                          columnNames = "property_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransportationAnalysis extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "dist_metro_rail")
    private Double distMetroRail;

    @Column(name = "dist_metro_mover")
    private Double distMetroMover;

    @Column(name = "dist_bus_stop")
    private Double distBusStop;

    @Column(name = "dist_highway")
    private Double distHighway;

    @Column(name = "bus_stops_within_500m")
    private Integer busStopsWithin500m;

    @Column(name = "transportation_score")
    private Double transportationScore;

    @Column(name = "metro_rail_score")
    private Double metroRailScore;

    @Column(name = "metro_mover_score")
    private Double metroMoverScore;

    @Column(name = "bus_stop_density_score")
    private Double busStopDensityScore;

    @Column(name = "bus_stop_distance_score")
    private Double busStopDistanceScore;

    @Column(name = "highway_score")
    private Double highwayScore;
}
