package com.geointelli.ai.property.service.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Read-only queries for the price-divergence explanation: the property's group keys and the stored ZentraValue
 * estimates of the other properties of each group. Only estimates with status supported or limited are used.
 */
public interface PriceDivergenceReader {

    /** Benchmark groups, from the most to the least specific. */
    enum Level {
        /** Units sharing the same parent property. */
        BUILDING,
        /** Same county and subdivision. */
        COMMUNITY,
        /** Same county and neighborhood. */
        MICRO_MARKET
    }

    /**
     * Group keys of the property. Blank and "0" subdivision or neighborhood values are returned as null.
     *
     * @param id                property ID
     * @param folio             county folio
     * @param countyId          county ID
     * @param parentPropertyId  parent property ID, null when the property is not a unit
     * @param community         subdivision, trimmed
     * @param neighborhood      neighborhood, trimmed
     * @param livingAreaSqft    heated living area
     */
    record Subject(Long id, String folio, Long countyId, Long parentPropertyId, String community,
                   String neighborhood, BigDecimal livingAreaSqft) {
    }

    /**
     * Estimates of the other properties of one group.
     *
     * @param sampleSize         properties with a usable estimate
     * @param medianValue        median estimate, null when the sample is empty
     * @param averageValue       average estimate, null when the sample is empty
     * @param medianPricePerSqft median estimate per sq ft of the members with a living area
     * @param valuationsFrom     oldest prediction date
     * @param valuationsTo       newest prediction date
     */
    record GroupValuations(long sampleSize, BigDecimal medianValue, BigDecimal averageValue,
                           BigDecimal medianPricePerSqft, LocalDate valuationsFrom, LocalDate valuationsTo) {
    }

    /**
     * One group member with stored valuation factors.
     *
     * @param estimate its estimate
     * @param factors  its valuation_factors JSON
     */
    record MemberFactors(BigDecimal estimate, String factors) {
    }

    /**
     * Reads the group keys of a property.
     *
     * @param propertyId property ID
     * @return the keys, empty if the property does not exist
     */
    Optional<Subject> findSubject(Long propertyId);

    /**
     * Summarises the estimates of the other properties of the subject's group. The subject's key for this level
     * must not be null.
     *
     * @param level   group level
     * @param subject the property
     * @return the sample size, median, average and prediction dates
     */
    GroupValuations groupValuations(Level level, Subject subject);

    /**
     * Returns the estimate and stored valuation_factors JSON of the same properties as {@link #groupValuations},
     * for those that have factors.
     *
     * @param level   group level
     * @param subject the property
     * @return one entry per property that has factors
     */
    List<MemberFactors> groupValuationFactors(Level level, Subject subject);


}
