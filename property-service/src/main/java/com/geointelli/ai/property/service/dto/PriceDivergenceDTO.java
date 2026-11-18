package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Explains how far a property's ZentraValue estimate is from its building, community and micro-market benchmarks,
 * and which valuation factors account for the gap. Every value that lacks evidence is null with a reason; nothing is
 * estimated here.
 *
 * @param propertyId           property ID
 * @param folio                county folio
 * @param observedAt           when the benchmarks were read
 * @param subject              the property's own valuation
 * @param benchmarks           one entry per level: BUILDING, COMMUNITY, MICRO_MARKET
 * @param primaryBenchmark     the most specific AVAILABLE level, or null when none has enough evidence
 * @param drivers              valuation factors compared with the primary benchmark group
 * @param unexplainedResidual  part of the primary divergence that the factors do not account for
 * @param confidence           how much the explanation can be trusted
 * @param askingPriceComparison asking-price comparison, kept separate from the valuation benchmarks
 * @param note                 how the values are built
 */
public record PriceDivergenceDTO(Long propertyId, String folio, Instant observedAt, Subject subject,
                                 List<Benchmark> benchmarks, String primaryBenchmark, Drivers drivers, Residual unexplainedResidual,
                                 Confidence confidence, AskingPriceComparison askingPriceComparison, String note) {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String BASIS = "ZENTRAVALUE_ESTIMATES";
    public static final String DRIVER_SOURCE = "ZentraValue valuation factors";
    public static final String NOTE = "Benchmarks are the median ZentraValue estimates of other properties in the same "
            + "group, not completed sales. Driver amounts are differences between the model's own valuation factors; "
            + "none are estimated here. The residual also includes the gap between the group median and its average.";

    /**
     * The property's own valuation.
     *
     * @param valuationStatus     supported, limited or unsupported, as stored
     * @param estimate            estimated value, null when there is no estimate
     * @param livingAreaSqft      heated living area, null when not recorded
     * @param pricePerSqft        estimate divided by the living area, null when either is missing
     * @param valuationDate       prediction date of the estimate
     * @param valuationConfidence confidence stored with the estimate
     */
    public record Subject(String valuationStatus, BigDecimal estimate, BigDecimal livingAreaSqft,
                          BigDecimal pricePerSqft, LocalDate valuationDate, String valuationConfidence) {
    }

    /**
     * Benchmark of one group.
     *
     * @param level              BUILDING, COMMUNITY or MICRO_MARKET
     * @param status             AVAILABLE or UNAVAILABLE
     * @param groupKey           parent property ID, subdivision or neighborhood; null when the property has none
     * @param basis              what the benchmark is built from
     * @param sampleSize         other properties of the group with a usable estimate
     * @param medianValue        median estimate of the group
     * @param medianPricePerSqft median estimate per sq ft of the group members with a living area
     * @param divergenceUsd      property estimate minus medianValue
     * @param divergencePct      divergenceUsd as a percentage of medianValue, two decimals
     * @param valuationsFrom     oldest prediction date in the sample
     * @param valuationsTo       newest prediction date in the sample
     * @param reason             why a value is missing, null otherwise
     */
    public record Benchmark(String level, String status, String groupKey, String basis, long sampleSize,
                            BigDecimal medianValue, BigDecimal medianPricePerSqft, BigDecimal divergenceUsd, BigDecimal divergencePct,
                            LocalDate valuationsFrom, LocalDate valuationsTo, String reason) {
    }

    /**
     * Valuation factors of the property compared with the average factors of the primary benchmark group.
     *
     * @param status       AVAILABLE or UNAVAILABLE
     * @param source       where the amounts come from
     * @param comparedWith level of the group the factors are compared with
     * @param sampleSize   group members whose factors could be read
     * @param factors      one entry per factor, largest difference first
     * @param explainedUsd sum of the differences
     * @param reason       why the drivers are unavailable, null otherwise
     */
    public record Drivers(String status, String source, String comparedWith, long sampleSize,
                          List<DriverFactor> factors, BigDecimal explainedUsd, String reason) {
    }

    /**
     * One valuation factor.
     *
     * @param code            factor code of the model
     * @param label           display label
     * @param subjectUsd      effect on the property's estimate (0 when the model did not list it)
     * @param groupAverageUsd average effect in the group (a member that does not list it counts 0)
     * @param differenceUsd   subjectUsd minus groupAverageUsd
     */
    public record DriverFactor(String code, String label, BigDecimal subjectUsd, BigDecimal groupAverageUsd,
                               BigDecimal differenceUsd) {
    }

    /**
     * Part of the primary divergence that the drivers do not account for.
     *
     * @param status    AVAILABLE or UNAVAILABLE
     * @param benchmark level the divergence is measured against
     * @param usd       primary divergence minus explainedUsd
     * @param reason    why it is unavailable, null otherwise
     */
    public record Residual(String status, String benchmark, BigDecimal usd, String reason) {
    }

    /**
     * Confidence of the explanation.
     *
     * @param level  the stored valuation confidence, or UNAVAILABLE
     * @param reason what the level is based on
     */
    public record Confidence(String level, String reason) {
    }

    /**
     * Asking-price comparison, never mixed with the valuation benchmarks.
     *
     * @param status        AVAILABLE or UNAVAILABLE
     * @param askingPrice   recorded asking price
     * @param differenceUsd estimate minus asking price
     * @param reason        why it is unavailable, null otherwise
     */
    public record AskingPriceComparison(String status, BigDecimal askingPrice, BigDecimal differenceUsd,
                                        String reason) {
    }
}
