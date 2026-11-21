package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.PriceDivergenceDTO;

/**
 * Explains a property's price divergence from its building, community and micro-market benchmarks, built from the
 * stored ZentraValue estimates of the other properties of each group. Read-only.
 */
public interface PriceDivergenceService {

    /**
     * Compares the property's estimate with each benchmark and splits the divergence from the most specific available
     * benchmark into valuation-factor differences and an unexplained residual. Values without enough evidence are
     * returned as null with a reason.
     *
     * @param propertyId property ID
     * @return the explanation
     * @throws IllegalArgumentException if the ID is not positive
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException if the property does not exist
     */
    PriceDivergenceDTO getDivergence(Long propertyId);
}
