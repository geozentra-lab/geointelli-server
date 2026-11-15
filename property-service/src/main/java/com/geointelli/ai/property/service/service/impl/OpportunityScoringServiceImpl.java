package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.OpportunityScoreDTO;
import com.geointelli.ai.property.service.dto.OpportunityScoreDTO.Factor;
import com.geointelli.ai.property.service.dto.OpportunityScoreDTO.Reason;
import com.geointelli.ai.property.service.dto.OpportunityScoreDTO.ValuationBasis;
import com.geointelli.ai.property.service.dto.OpportunitySignals;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.OpportunityScoreJdbcRepository;
import com.geointelli.ai.property.service.service.OpportunityScoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Opportunity scoring rules {@value OpportunityScoringService#SCORE_VERSION}. Each factor maps one piece of
 * evidence linearly onto 0 to 100 (clamped) and earns {@code weight × subscore / 100} points; the weights add up
 * to 100. Factors without evidence are UNAVAILABLE: they earn no points and lower the coverage, so missing data can
 * never raise a score. A score needs a supported or limited valuation with an estimate and a one-year forecast;
 * without them it is UNAVAILABLE.
 */
@Service
public class OpportunityScoringServiceImpl implements OpportunityScoringService {

    static final String SCORED = OpportunityScoreDTO.STATUS_SCORED;
    static final String UNAVAILABLE = OpportunityScoreDTO.STATUS_UNAVAILABLE;
    /** Factor status when its evidence exists; factors without evidence are {@link #UNAVAILABLE}. */
    static final String AVAILABLE = "AVAILABLE";

    static final int WEIGHT_GROWTH_1Y = 20;
    static final int WEIGHT_GROWTH_3Y = 15;
    static final int WEIGHT_MARKET_OUTLOOK = 5;
    static final int WEIGHT_VALUATION_CONFIDENCE = 10;
    static final int WEIGHT_RENTAL_YIELD = 20;
    static final int WEIGHT_FINANCED_CASH_FLOW = 10;
    static final int WEIGHT_ASKING_PRICE_DISCOUNT = 20;

    /** Signal ranges that map onto subscores 0 and 100 (percent, except the confidence score). */
    static final BigDecimal GROWTH_1Y_LOW = BigDecimal.valueOf(-10);
    static final BigDecimal GROWTH_1Y_HIGH = BigDecimal.valueOf(10);
    static final BigDecimal GROWTH_3Y_LOW = BigDecimal.valueOf(-25);
    static final BigDecimal GROWTH_3Y_HIGH = BigDecimal.valueOf(25);
    static final BigDecimal OUTLOOK_LOW = BigDecimal.valueOf(-10);
    static final BigDecimal OUTLOOK_HIGH = BigDecimal.valueOf(10);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext MC = MathContext.DECIMAL64;

    private final OpportunityScoreJdbcRepository repository;
    private final Clock clock;

    @Autowired
    public OpportunityScoringServiceImpl(OpportunityScoreJdbcRepository repository) {
        this(repository, Clock.systemUTC());
    }

    OpportunityScoringServiceImpl(OpportunityScoreJdbcRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public OpportunityScoreDTO getScore(Long propertyId) {
        if (propertyId == null || propertyId <= 0) {
            throw new IllegalArgumentException("propertyId must be positive");
        }
        OpportunitySignals signals = repository.findSignals(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property not found: " + propertyId));
        return score(signals, clock.instant());
    }

    @Override
    public OpportunityScoreDTO score(OpportunitySignals signals, Instant scoredAt) {
        List<Reason> reasons = new ArrayList<>();
        String valuationStatus = signals.valuationStatus();
        BigDecimal value = signals.estimatedValue();
        if (!signals.hasValuation()) {
            reasons.add(new Reason("NO_VALUATION", "No ZentraValue valuation is stored for this property."));
        } else if (!"supported".equals(valuationStatus) && !"limited".equals(valuationStatus)) {
            reasons.add(new Reason("VALUATION_UNSUPPORTED",
                    "The valuation model does not support an estimate for this property."));
        } else if (value == null || value.signum() <= 0) {
            reasons.add(new Reason("NO_ESTIMATE", "The stored valuation has no estimated value."));
        }
        boolean usableValuation = reasons.isEmpty();
        if (usableValuation && signals.forecast1y() == null) {
            reasons.add(new Reason("NO_FORECAST", "No one-year forecast is stored for this property."));
        }

        List<Factor> factors = new ArrayList<>();
        factors.add(usableValuation && signals.forecast1y() != null
                ? growth("FORECAST_GROWTH_1Y", "One-year forecast growth", WEIGHT_GROWTH_1Y, "One-year",
                signals.forecast1y(), value, GROWTH_1Y_LOW, GROWTH_1Y_HIGH)
                : unavailable("FORECAST_GROWTH_1Y", "One-year forecast growth", "MARKET", WEIGHT_GROWTH_1Y,
                usableValuation ? "No one-year forecast is stored." : "No usable valuation to compare a forecast with."));
        factors.add(usableValuation && signals.forecast3y() != null
                ? growth("FORECAST_GROWTH_3Y", "Three-year forecast growth", WEIGHT_GROWTH_3Y, "Three-year",
                signals.forecast3y(), value, GROWTH_3Y_LOW, GROWTH_3Y_HIGH)
                : unavailable("FORECAST_GROWTH_3Y", "Three-year forecast growth", "MARKET", WEIGHT_GROWTH_3Y,
                usableValuation ? "No three-year forecast is stored." : "No usable valuation to compare a forecast with."));
        factors.add(usableValuation && signals.marketOutlook1yPct() != null && Double.isFinite(signals.marketOutlook1yPct())
                ? outlook(BigDecimal.valueOf(signals.marketOutlook1yPct()))
                : unavailable("MARKET_OUTLOOK", "Area market outlook", "MARKET", WEIGHT_MARKET_OUTLOOK,
                usableValuation ? "No market outlook is stored for the property's area."
                        : "No usable valuation, so its market outlook is not used."));
        factors.add(usableValuation && signals.confidenceScore() != null
                ? confidence(signals.confidenceScore(), signals.confidence())
                : unavailable("VALUATION_CONFIDENCE", "Valuation confidence", "VALUATION", WEIGHT_VALUATION_CONFIDENCE,
                usableValuation ? "No valuation confidence score is stored." : "No usable valuation."));
        factors.add(unavailable("RENTAL_YIELD", "Rental yield", "RENT", WEIGHT_RENTAL_YIELD,
                "No rent estimate is stored for this property, so rental yield cannot be measured."));
        factors.add(unavailable("FINANCED_CASH_FLOW", "Cash flow after financing", "FINANCING", WEIGHT_FINANCED_CASH_FLOW,
                "Needs a rent estimate and financing terms; neither is stored for this property."));
        factors.add(unavailable("ASKING_PRICE_DISCOUNT", "Discount of the asking price to the estimate", "PRICE",
                WEIGHT_ASKING_PRICE_DISCOUNT,
                "No listing price is known, so a discount to the estimated value cannot be measured."));

        BigDecimal coverage = BigDecimal.ZERO;
        BigDecimal points = BigDecimal.ZERO;
        for (Factor factor : factors) {
            if (AVAILABLE.equals(factor.status())) {
                coverage = coverage.add(BigDecimal.valueOf(factor.weight()));
                points = points.add(factor.points());
            }
        }
        boolean scored = reasons.isEmpty();
        ValuationBasis valuation = signals.hasValuation()
                ? new ValuationBasis(value, valuationStatus, signals.confidence(), signals.modelVersion(),
                signals.dataThrough(), signals.valuationRunId())
                : null;
        return new OpportunityScoreDTO(signals.propertyId(), SCORE_VERSION, scored ? SCORED : UNAVAILABLE,
                scored ? points.setScale(2, RoundingMode.HALF_UP) : null, MAX_SCORE,
                coverage.setScale(2, RoundingMode.HALF_UP), List.copyOf(factors), List.copyOf(reasons), valuation,
                scoredAt, NOTES);
    }

    /** Forecast value against the current estimate, as a percentage change. */
    private static Factor growth(String code, String label, int weight, String horizon, BigDecimal forecast,
                                 BigDecimal value, BigDecimal low, BigDecimal high) {
        BigDecimal growthPct = forecast.divide(value, MC).subtract(BigDecimal.ONE).multiply(HUNDRED);
        BigDecimal subscore = subscore(growthPct, low, high);
        String explanation = String.format(Locale.ROOT,
                "%s forecast %s%% against the current estimate (%s to %s). Scale: %s%% scores 0, %s%% scores 100.",
                horizon, signed(growthPct), money(value), money(forecast), signed(low), signed(high));
        return available(code, label, "MARKET", weight, growthPct, "PERCENT", subscore, explanation);
    }

    private static Factor outlook(BigDecimal outlookPct) {
        BigDecimal subscore = subscore(outlookPct, OUTLOOK_LOW, OUTLOOK_HIGH);
        String explanation = String.format(Locale.ROOT,
                "The area's one-year market outlook is %s%%. Scale: %s%% scores 0, %s%% scores 100.",
                signed(outlookPct), signed(OUTLOOK_LOW), signed(OUTLOOK_HIGH));
        return available("MARKET_OUTLOOK", "Area market outlook", "MARKET", WEIGHT_MARKET_OUTLOOK, outlookPct,
                "PERCENT", subscore, explanation);
    }

    private static Factor confidence(int confidenceScore, String label) {
        BigDecimal signal = BigDecimal.valueOf(confidenceScore);
        BigDecimal subscore = subscore(signal, BigDecimal.ZERO, HUNDRED);
        String explanation = String.format(Locale.ROOT,
                "The valuation's confidence score is %d of 100%s; it is used as the subscore.",
                confidenceScore, label == null ? "" : " (" + label + " confidence)");
        return available("VALUATION_CONFIDENCE", "Valuation confidence", "VALUATION", WEIGHT_VALUATION_CONFIDENCE,
                signal, "SCORE", subscore, explanation);
    }

    private static Factor available(String code, String label, String category, int weight, BigDecimal signal,
                                    String unit, BigDecimal subscore, String explanation) {
        BigDecimal points = subscore.multiply(BigDecimal.valueOf(weight)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new Factor(code, label, category, weight, AVAILABLE, signal.setScale(2, RoundingMode.HALF_UP), unit,
                subscore, points, explanation);
    }

    private static Factor unavailable(String code, String label, String category, int weight, String reason) {
        return new Factor(code, label, category, weight, UNAVAILABLE, null, null, null, null,
                "Unavailable: " + reason + " It adds no points.");
    }

    /** Maps the signal linearly from [low, high] onto [0, 100], clamped, with 2 decimals. */
    static BigDecimal subscore(BigDecimal signal, BigDecimal low, BigDecimal high) {
        BigDecimal scaled = signal.subtract(low).multiply(HUNDRED).divide(high.subtract(low), MC);
        return scaled.max(BigDecimal.ZERO).min(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    private static String signed(BigDecimal percent) {
        BigDecimal rounded = percent.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        String text = rounded.scale() < 0 ? rounded.setScale(0).toPlainString() : rounded.toPlainString();
        return rounded.signum() > 0 ? "+" + text : text;
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.ROOT, "$%,.0f", amount);
    }
}
