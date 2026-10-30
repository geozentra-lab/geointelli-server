package com.geointelli.ai.property.service.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * Side-by-side comparison of the requested properties. Columns keep the request order; every
 * column uses the same units and, when possible, the same assessment year.
 */
@Data
public class PropertyCompareResponse {
    /** The IDs exactly as requested. */
    private List<Long> requestedIds;
    private int comparedCount;

    /** Unit of each measured field, e.g. currency=USD, area=sqft. Identical for every column. */
    private Map<String, String> units;

    /**
     * Assessment year shared by all columns, chosen as the latest year every property has.
     * {@code null} when no common year exists; each column then shows its own latest year and a
     * warning explains the mismatch.
     */
    private Integer assessmentYear;

    /** Human-readable notes on comparability, e.g. differing assessment years. Empty when fully comparable. */
    private List<String> warnings;

    private List<PropertyComparisonDTO> properties;
}
