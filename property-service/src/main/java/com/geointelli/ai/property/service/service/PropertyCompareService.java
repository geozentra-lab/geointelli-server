package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.PropertyCompareRequest;
import com.geointelli.ai.property.service.dto.PropertyCompareResponse;

/**
 * Builds a side-by-side comparison of user-selected properties from the existing property,
 * valuation and analysis services. It only assembles their outputs; it never recomputes a
 * valuation or an investment formula.
 */
public interface PropertyCompareService {

    /**
     * Compares the requested properties in the order given.
     * <p>
     * Facts come from {@link PropertyService}, the valuation from
     * {@link PropertyValuePredictionService}, the scores from {@link SchoolAnalysisService} and
     * {@link TransportationAnalysisService}. Every column uses the same units and the latest
     * assessment year all properties share; when no common year exists each column uses its own
     * latest year and a warning is added. A value that does not exist is {@code null} and is
     * listed in the column's {@code missingFields}.
     *
     * @param request {@value PropertyCompareRequest#MIN_PROPERTIES} to
     *                {@value PropertyCompareRequest#MAX_PROPERTIES} distinct internal property IDs
     * @return one column per requested ID, in request order
     * @throws IllegalArgumentException if the list contains duplicates
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException
     *         if any ID does not match a property
     */
    PropertyCompareResponse compare(PropertyCompareRequest request);
}
