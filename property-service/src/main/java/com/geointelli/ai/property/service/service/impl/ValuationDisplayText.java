package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/** Labels and messages from the Python VALUATION_CONTRACT.md, kept together for display. */
@Slf4j
final class ValuationDisplayText {
    private static final Map<String, String> REASONS = Map.ofEntries(
        Map.entry("no_estimate", "The model could not value this property."),
        Map.entry("below_validated_range", "Estimated value is below the range the model has been tested on."),
        Map.entry("above_validated_range", "Estimated value is above the range the model has been tested on."),
        Map.entry("high_value_segment", "High-value properties have wider errors (about 33% median in testing)."),
        Map.entry("low_confidence", "Similar properties had large errors in testing; treat as indicative."),
        Map.entry("no_sale_history", "No recorded sale; the estimate relies on attributes and nearby sales."),
        Map.entry("no_living_area", "Living area is not recorded for this property."),
        Map.entry("no_forecast", "Forecasts are not available for this property type or location."),
        Map.entry("not_modelled", "This property type is not covered by the model yet."));

    private static final Map<String, String> FACTORS = Map.ofEntries(
        Map.entry("living_area", "Living area"), Map.entry("rooms", "Bedrooms and bathrooms"),
        Map.entry("lot_and_land", "Lot and land"), Map.entry("age_and_condition", "Age and condition"),
        Map.entry("county_assessment", "County assessment"), Map.entry("location", "Location"),
        Map.entry("nearby_sales", "Nearby sales"), Map.entry("sale_history", "Sale history"),
        Map.entry("property_type", "Property type"), Map.entry("market_timing", "Market timing"),
        Map.entry("other", "Other factors"));

    private static final Map<String, String> SUBJECTS = Map.ofEntries(
        Map.entry("living_area", "Its living area"),
        Map.entry("rooms", "Its bedroom and bathroom configuration"),
        Map.entry("lot_and_land", "Its lot and land contribution"),
        Map.entry("age_and_condition", "Its age and condition contribution"),
        Map.entry("county_assessment", "Its county assessment"),
        Map.entry("location", "Its location"),
        Map.entry("nearby_sales", "Nearby sale evidence"),
        Map.entry("sale_history", "Its last sale price"),
        Map.entry("property_type", "Its property type"),
        Map.entry("market_timing", "Market timing"),
        Map.entry("other", "The remaining model contribution"));

    private ValuationDisplayText() {}

    static String reason(String code) {
        String message = REASONS.get(code);
        if (message == null) {
            log.warn("Unknown valuation reason code: {}", code);
            return code;
        }
        return message;
    }

    static String label(String code) {
        String label = FACTORS.get(code);
        if (label == null) {
            log.warn("Unknown valuation factor code: {}", code);
            return code;
        }
        return label;
    }

    static String factorText(String code, String label, BigDecimal pct) {
        if (pct == null) return label + ": percentage effect is unavailable.";
        String subject = SUBJECTS.getOrDefault(code, "The " + label + " factor");
        if (pct.signum() == 0) return subject + " has no percentage effect on the value.";
        String direction = pct.signum() > 0 ? "raises" : "lowers";
        return subject + " " + direction + " the value by about "
            + pct.abs().setScale(0, RoundingMode.HALF_UP).toPlainString() + "%.";
    }
}
