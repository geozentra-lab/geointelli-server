package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ForecastResponse;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;

/** Read-only access to model forecasts. */
public interface ForecastService {
    /**
     * Reads stored one-, three- and five-year projections (seven-year on valuations
     * published before the 5-year forecast replaced it) without recalculating
     * them.
     *
     * @param propertyId
     *                       positive property ID
     * @return forecasts or explicit unavailability for an existing property
     * @throws IllegalArgumentException
     *                                       if the ID is invalid
     * @throws PropertyNotFoundException
     *                                       if the property does not exist
     */
    ForecastResponse getForecast(Long propertyId);
}
