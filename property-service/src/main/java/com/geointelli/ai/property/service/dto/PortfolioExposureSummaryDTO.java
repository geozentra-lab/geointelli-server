package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
/** Current owner-reported occupancy and current property geography; no historical occupancy is inferred. */
public record PortfolioExposureSummaryDTO(String status,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate asOfDate,
    long totalHoldings, Map<HoldingOccupancyStatus,Long> occupancyCounts,
    long knownOccupancyHoldings, long rentalHoldings, BigDecimal rentalOccupancyPercent,
    String exposureBasis, List<PortfolioGeographyDTO> states, List<PortfolioGeographyDTO> counties,
    @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDateTime holdingsUpdatedAt, String note) {}
