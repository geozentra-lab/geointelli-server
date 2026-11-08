package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValuationDisplayTextTest {

    @ParameterizedTest
    @CsvSource({
        "no_estimate, The model could not value this property.",
        "not_modelled, This property type is not covered by the model yet.",
        "no_forecast, Forecasts are not available for this property type or location."
    })
    @DisplayName("When the reason code is known, then its message is returned")
    void whenKnownReason_thenMessage(String code, String message) {
        assertThat(ValuationDisplayText.reason(code)).isEqualTo(message);
    }

    @Test
    @DisplayName("When the reason code or factor code is unknown, then the code itself is returned")
    void whenUnknownCode_thenCodeReturned() {
        assertThat(ValuationDisplayText.reason("future_reason")).isEqualTo("future_reason");
        assertThat(ValuationDisplayText.label("future_factor")).isEqualTo("future_factor");
    }

    @ParameterizedTest
    @CsvSource({
        "living_area, Living area",
        "rooms, Bedrooms and bathrooms",
        "county_assessment, County assessment",
        "other, Other factors"
    })
    @DisplayName("When the factor code is known, then its label is returned")
    void whenKnownFactor_thenLabel(String code, String label) {
        assertThat(ValuationDisplayText.label(code)).isEqualTo(label);
    }

    @ParameterizedTest
    @CsvSource({
        "living_area, 12.4, Its living area raises the value by about 12%.",
        "living_area, 12.5, Its living area raises the value by about 13%.",
        "location, -4.6, Its location lowers the value by about 5%.",
        "sale_history, 0, Its last sale price has no percentage effect on the value.",
        "nearby_sales, 3, Nearby sale evidence raises the value by about 3%."
    })
    @DisplayName("When the percentage is known, then the text states the direction and the rounded size")
    void whenPct_thenDirectionAndRoundedSize(String code, String pct, String text) {
        assertThat(ValuationDisplayText.factorText(code, ValuationDisplayText.label(code), new BigDecimal(pct)))
            .isEqualTo(text);
    }

    @Test
    @DisplayName("When the percentage is missing, then the text says the effect is unavailable")
    void whenPctMissing_thenUnavailable() {
        assertThat(ValuationDisplayText.factorText("living_area", "Living area", null))
            .isEqualTo("Living area: percentage effect is unavailable.");
    }

    @Test
    @DisplayName("When the factor code is unknown, then the text uses its label as the subject")
    void whenUnknownFactor_thenGenericSubject() {
        assertThat(ValuationDisplayText.factorText("future_factor", "future_factor", new BigDecimal("-2")))
            .isEqualTo("The future_factor factor lowers the value by about 2%.");
    }
}
