package com.geointelli.ai.property.service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.PriceDivergenceDTO;
import com.geointelli.ai.property.service.dto.PriceDivergenceDTO.AskingPriceComparison;
import com.geointelli.ai.property.service.dto.PriceDivergenceDTO.Benchmark;
import com.geointelli.ai.property.service.dto.PriceDivergenceDTO.Confidence;
import com.geointelli.ai.property.service.dto.PriceDivergenceDTO.DriverFactor;
import com.geointelli.ai.property.service.dto.PriceDivergenceDTO.Drivers;
import com.geointelli.ai.property.service.dto.PriceDivergenceDTO.Residual;
import com.geointelli.ai.property.service.dto.PriceDivergenceDTO.Subject;
import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.dto.ValuationResponse.Factor;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PriceDivergenceReader;
import com.geointelli.ai.property.service.repository.PriceDivergenceReader.GroupValuations;
import com.geointelli.ai.property.service.repository.PriceDivergenceReader.Level;
import com.geointelli.ai.property.service.repository.PriceDivergenceReader.MemberFactors;
import com.geointelli.ai.property.service.service.PriceDivergenceService;
import com.geointelli.ai.property.service.service.ValuationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Benchmarks are medians of the other properties' stored estimates; drivers are differences between the property's
 * valuation factors and the group's average factors, and explain the gap from the group's average estimate over the
 * same members. The residual is the part of that gap the factors' dollar amounts do not add up to, because the model
 * multiplies its factors. Nothing is estimated: a value without evidence is null with a reason.
 */
@Service
@RequiredArgsConstructor
public class PriceDivergenceServiceImpl implements PriceDivergenceService {

    /** Fewest other valued properties a benchmark, or its driver comparison, needs. */
    static final int MIN_SAMPLE = 5;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PriceDivergenceReader reader;
    private final ValuationService valuationService;
    private final ObjectMapper json;

    /** {@inheritDoc} */
    @Override
    public PriceDivergenceDTO getDivergence(Long propertyId) {
        if (propertyId == null || propertyId <= 0) {
            throw new IllegalArgumentException("propertyId must be positive");
        }
        PriceDivergenceReader.Subject property = reader.findSubject(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property not found: " + propertyId));
        Instant observedAt = Instant.now();
        ValuationResponse valuation = valuationService.getValuationWithoutComparables(propertyId);
        BigDecimal estimate = valuation.estimate() == null ? null : valuation.estimate().value();

        List<Benchmark> benchmarks = new ArrayList<>();
        for (Level level : Level.values()) {
            benchmarks.add(benchmark(level, property, estimate));
        }
        Benchmark primary = benchmarks.stream()
                .filter(b -> PriceDivergenceDTO.AVAILABLE.equals(b.status()))
                .findFirst().orElse(null);

        Drivers drivers = drivers(primary, property, valuation);
        return new PriceDivergenceDTO(propertyId, property.folio(), observedAt, subject(property, valuation),
                benchmarks, primary == null ? null : primary.level(), drivers, residual(primary, drivers),
                confidence(primary, valuation), askingPrice(), PriceDivergenceDTO.NOTE);
    }

    /** The property's own estimate, area and price per sq ft. */
    private static Subject subject(PriceDivergenceReader.Subject property, ValuationResponse valuation) {
        BigDecimal estimate = valuation.estimate() == null ? null : valuation.estimate().value();
        BigDecimal area = positive(property.livingAreaSqft());
        BigDecimal perSqft = estimate == null || area == null ? null : estimate.divide(area, 2, RoundingMode.HALF_UP);
        return new Subject(valuation.status(), estimate, area, perSqft,
                valuation.method() == null ? null : valuation.method().predictionDate(),
                valuation.estimate() == null ? null : valuation.estimate().confidence());
    }

    /** Median of the group's other estimates and the property's divergence from it. */
    private Benchmark benchmark(Level level, PriceDivergenceReader.Subject property, BigDecimal estimate) {
        String key = groupKey(level, property);
        if (key == null) {
            return new Benchmark(level.name(), PriceDivergenceDTO.UNAVAILABLE, null, PriceDivergenceDTO.BASIS, 0,
                    null, null, null, null, null, null, null, missingKeyReason(level));
        }
        GroupValuations group = reader.groupValuations(level, property);
        if (group.sampleSize() < MIN_SAMPLE) {
            return new Benchmark(level.name(), PriceDivergenceDTO.UNAVAILABLE, key, PriceDivergenceDTO.BASIS,
                    group.sampleSize(), null, null, null, null, null, group.valuationsFrom(), group.valuationsTo(),
                    "Only " + group.sampleSize() + " other valued properties in this group (minimum " + MIN_SAMPLE + ")");
        }
        BigDecimal median = scale(group.medianValue());
        BigDecimal divergence = estimate == null ? null : estimate.subtract(median);
        BigDecimal percent = divergence == null ? null
                : divergence.multiply(HUNDRED).divide(median, 2, RoundingMode.HALF_UP);
        return new Benchmark(level.name(), PriceDivergenceDTO.AVAILABLE, key, PriceDivergenceDTO.BASIS,
                group.sampleSize(), median, scale(group.averageValue()), scale(group.medianPricePerSqft()),
                divergence, percent, group.valuationsFrom(), group.valuationsTo(),
                estimate == null ? "The property has no estimate to compare" : null);
    }

    private static String groupKey(Level level, PriceDivergenceReader.Subject property) {
        return switch (level) {
            case BUILDING -> property.parentPropertyId() == null ? null : property.parentPropertyId().toString();
            case COMMUNITY -> property.community();
            case MICRO_MARKET -> property.neighborhood();
        };
    }

    private static String missingKeyReason(Level level) {
        return switch (level) {
            case BUILDING -> "The property is not a unit of a building";
            case COMMUNITY -> "No subdivision is recorded for the property";
            case MICRO_MARKET -> "No neighborhood is recorded for the property";
        };
    }

    /**
     * Property factors minus the average factors of the primary group, largest difference first, with the gap from
     * the average estimate of the same members.
     */
    private Drivers drivers(Benchmark primary, PriceDivergenceReader.Subject property, ValuationResponse valuation) {
        if (primary == null) {
            return unavailableDrivers(null, 0, "No benchmark has enough evidence");
        }
        if (primary.divergenceUsd() == null) {
            return unavailableDrivers(primary.level(), 0, "The property has no estimate to compare");
        }
        Map<String, BigDecimal> subjectFactors = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        if (valuation.explanation() != null) {
            for (Factor factor : valuation.explanation().factors()) {
                if (factor.usd() != null) {
                    subjectFactors.merge(factor.code(), factor.usd(), BigDecimal::add);
                    labels.put(factor.code(), factor.label());
                }
            }
        }
        if (subjectFactors.isEmpty()) {
            return unavailableDrivers(primary.level(), 0, "Model explanation factors are unavailable for the property");
        }

        Level level = Level.valueOf(primary.level());
        List<Map<String, BigDecimal>> group = new ArrayList<>();
        // the average estimate is taken over the same members as the average factors, so the residual holds no
        // difference between two samples
        BigDecimal groupEstimates = BigDecimal.ZERO;
        for (MemberFactors member : reader.groupValuationFactors(level, property)) {
            Map<String, BigDecimal> factors = member.estimate() == null ? null : parseFactors(member.factors());
            if (factors != null) {
                group.add(factors);
                groupEstimates = groupEstimates.add(member.estimate());
            }
        }
        if (group.size() < MIN_SAMPLE) {
            return unavailableDrivers(primary.level(), group.size(), "Only " + group.size()
                    + " properties of the benchmark group have valuation factors (minimum " + MIN_SAMPLE + ")");
        }

        Map<String, BigDecimal> groupTotals = new LinkedHashMap<>();
        for (Map<String, BigDecimal> factors : group) {
            factors.forEach((code, usd) -> groupTotals.merge(code, usd, BigDecimal::add));
        }
        List<String> codes = new ArrayList<>(subjectFactors.keySet());
        groupTotals.keySet().stream().filter(code -> !subjectFactors.containsKey(code)).forEach(codes::add);

        BigDecimal count = BigDecimal.valueOf(group.size());
        List<DriverFactor> factors = new ArrayList<>();
        BigDecimal explained = BigDecimal.ZERO;
        for (String code : codes) {
            BigDecimal subjectUsd = scale(subjectFactors.getOrDefault(code, BigDecimal.ZERO));
            BigDecimal average = groupTotals.getOrDefault(code, BigDecimal.ZERO).divide(count, 2, RoundingMode.HALF_UP);
            BigDecimal difference = subjectUsd.subtract(average);
            explained = explained.add(difference);
            factors.add(new DriverFactor(code, labels.getOrDefault(code, ValuationDisplayText.label(code)),
                    subjectUsd, average, difference));
        }
        factors.sort(Comparator.comparing((DriverFactor f) -> f.differenceUsd().abs()).reversed());
        BigDecimal groupAverage = groupEstimates.divide(count, 2, RoundingMode.HALF_UP);
        return new Drivers(PriceDivergenceDTO.AVAILABLE, PriceDivergenceDTO.DRIVER_SOURCE, primary.level(),
                group.size(), groupAverage, scale(valuation.estimate().value()).subtract(groupAverage),
                List.copyOf(factors), explained, null);
    }

    private static Drivers unavailableDrivers(String level, long sampleSize, String reason) {
        return new Drivers(PriceDivergenceDTO.UNAVAILABLE, PriceDivergenceDTO.DRIVER_SOURCE, level, sampleSize,
                null, null, List.of(), null, reason);
    }

    /**
     * Reads {"factors": [{"f": code, "usd": amount}]}; entries without a code or an amount are skipped.
     *
     * @return the amounts by code, or null when the text is not a factor object
     */
    private Map<String, BigDecimal> parseFactors(String text) {
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
        JsonNode entries = root == null ? null : root.get("factors");
        if (entries == null || !entries.isArray()) {
            return null;
        }
        Map<String, BigDecimal> factors = new HashMap<>();
        for (JsonNode entry : entries) {
            JsonNode code = entry.get("f");
            JsonNode usd = entry.get("usd");
            if (code != null && code.isTextual() && usd != null && usd.isNumber()) {
                factors.merge(code.textValue(), usd.decimalValue(), BigDecimal::add);
            }
        }
        return factors;
    }

    /**
     * Gap from the group average minus the explained amount: what the factors' dollar amounts do not add up to. It is
     * not measured from the median, so a group skewed by a few high or low estimates does not enter it.
     */
    private static Residual residual(Benchmark primary, Drivers drivers) {
        if (primary == null) {
            return new Residual(PriceDivergenceDTO.UNAVAILABLE, null, null, "No benchmark has enough evidence");
        }
        if (!PriceDivergenceDTO.AVAILABLE.equals(drivers.status())) {
            return new Residual(PriceDivergenceDTO.UNAVAILABLE, primary.level(), null,
                    "Drivers are unavailable: " + drivers.reason());
        }
        return new Residual(PriceDivergenceDTO.AVAILABLE, primary.level(),
                drivers.divergenceFromAverageUsd().subtract(drivers.explainedUsd()), null);
    }

    /** The stored valuation confidence, shown with the benchmark sample it is compared with. */
    private static Confidence confidence(Benchmark primary, ValuationResponse valuation) {
        String stored = valuation.estimate() == null ? null : valuation.estimate().confidence();
        if (primary == null) {
            return new Confidence(PriceDivergenceDTO.UNAVAILABLE, "No benchmark has enough evidence");
        }
        if (primary.divergenceUsd() == null || stored == null || stored.isBlank()) {
            return new Confidence(PriceDivergenceDTO.UNAVAILABLE, "The property has no estimate confidence");
        }
        String level = stored.toUpperCase(Locale.ROOT);
        return new Confidence(level, primary.level() + " benchmark from " + primary.sampleSize()
                + " valued properties; valuation confidence " + level);
    }

    /** No asking price is recorded anywhere, so it is never compared. */
    private static AskingPriceComparison askingPrice() {
        return new AskingPriceComparison(PriceDivergenceDTO.UNAVAILABLE, null, null,
                "No asking-price data is recorded");
    }

    private static BigDecimal positive(BigDecimal value) {
        return value == null || value.signum() <= 0 ? null : value;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
