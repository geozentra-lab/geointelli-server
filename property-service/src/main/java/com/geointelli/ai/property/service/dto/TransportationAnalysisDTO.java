package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransportationAnalysisDTO {

    private Long id;
    private Long propertyId;

    private BigDecimal distMetroRail;
    private BigDecimal distMetroMover;
    private BigDecimal distBusStop;
    private BigDecimal distHighway;

    private Integer busStopsWithin500m;

    private BigDecimal metroRailScore;
    private BigDecimal metroMoverScore;
    private BigDecimal busStopDistanceScore;
    private BigDecimal busStopDensityScore;
    private BigDecimal highwayScore;

    // Final weighted score
    private BigDecimal transportationScore;
}
