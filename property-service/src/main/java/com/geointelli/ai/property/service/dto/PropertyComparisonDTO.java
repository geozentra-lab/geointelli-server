package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;

/**
 * One column of the comparison table: the canonical facts, values and scores of one property,
 * copied from the existing services. A value that is not available is {@code null} and its
 * path is listed in {@link #missingFields}, so the client can show "n/a" instead of a blank.
 */
@Data
public class PropertyComparisonDTO {
    /** The ID that was requested; the position in the list matches the request order. */
    private Long propertyId;
    private String folio;
    private String address;
    private String countyName;
    private String stateCode;

    private Facts facts;
    private Assessment assessment;
    private LastSale lastSale;
    private Valuation valuation;
    private Scores scores;

    /** Paths of the fields that are {@code null} because the data does not exist, e.g. {@code valuation}, {@code facts.lotSizeSqft}. */
    private List<String> missingFields;

    /** Physical facts from the county record. Areas in square feet. */
    public record Facts(BigDecimal bedrooms, BigDecimal bathrooms, BigDecimal livingAreaSqft,
            Double lotSizeSqft, String yearBuilt, String propertyType, Integer floors, Integer units) {
    }

    /** County assessment for the comparison year, in USD. */
    public record Assessment(Integer year, BigDecimal totalValue, BigDecimal landValue,
            BigDecimal buildingValue, BigDecimal assessedValue) {
    }

    /** Most recent recorded sale, in USD. */
    public record LastSale(LocalDate date, BigDecimal price) {
    }

    /** Model valuation as produced by the prediction service, in USD; nothing is recomputed here. */
    public record Valuation(BigDecimal predictedPrice, BigDecimal low, BigDecimal high,
            BigDecimal growthPercentage, Integer confidenceScore, LocalDate predictionDate) {
    }

    /** Location scores as produced by the analysis services. */
    public record Scores(Double schoolScore, BigDecimal transportationScore) {
    }
}
