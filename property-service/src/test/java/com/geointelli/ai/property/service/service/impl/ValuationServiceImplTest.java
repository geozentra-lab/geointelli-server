package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionTimedOutException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.ComparableResponse;
import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.dto.ValuationResponse.Factor;
import com.geointelli.ai.property.service.dto.ValuationResponse.Reason;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.PropertyValuePredictionRepository;
import com.geointelli.ai.property.service.service.ComparableService;

class ValuationServiceImplTest {
    private static final long ID = 95340L;
    private static final String FORECAST_NOTE =
        "Three- and seven-year forecasts are trend scenarios; forecast ranges are illustrative.";

    private PropertyRepository propertyRepository;
    private PropertyValuePredictionRepository predictions;
    private ComparableService comparableService;
    private ValuationServiceImpl service;

    @BeforeEach
    void setUp() {
        propertyRepository = mock(PropertyRepository.class);
        predictions = mock(PropertyValuePredictionRepository.class);
        comparableService = mock(ComparableService.class);
        service = new ValuationServiceImpl(propertyRepository, predictions, comparableService, new ObjectMapper());
        when(propertyRepository.existsById(ID)).thenReturn(true);
    }

    private PropertyValuePrediction supported() {
        PropertyValuePrediction p = new PropertyValuePrediction();
        p.setPropertyId(ID);
        p.setValuationStatus("supported");
        p.setStatusReasons("[]");
        p.setPredictedPrice(new BigDecimal("822855.47"));
        p.setPredictedPriceLow(new BigDecimal("676666.91"));
        p.setPredictedPriceHigh(new BigDecimal("1000626.92"));
        p.setPredictedPriceLow90(new BigDecimal("612063.54"));
        p.setPredictedPriceHigh90(new BigDecimal("1106243.20"));
        p.setConfidence("high");
        p.setForecast1y(new BigDecimal("844881.25"));
        p.setForecast1yLow(new BigDecimal("386110.60"));
        p.setForecast1yHigh(new BigDecimal("1848756.09"));
        p.setForecast3y(new BigDecimal("906966.08"));
        p.setForecast3yLow(new BigDecimal("275678.47"));
        p.setForecast3yHigh(new BigDecimal("2983865.44"));
        p.setForecast7y(new BigDecimal("1045157.70"));
        p.setForecast7yLow(new BigDecimal("226584.78"));
        p.setForecast7yHigh(new BigDecimal("4820953.26"));
        p.setHasSaleHistory(true);
        p.setLastSalePrice(new BigDecimal("610000.00"));
        p.setLastSaleDate(LocalDate.of(2019, 5, 2));
        p.setValuationMethod("gbm_hedonic");
        p.setModelVersion("2026.10");
        p.setPredictionDate(LocalDate.of(2026, 10, 1));
        p.setDataThrough(LocalDate.of(2026, 9, 30));
        p.setCreatedAt(LocalDateTime.of(2026, 10, 2, 3, 0));
        p.setNotes("[\"Stored model note.\"]");
        p.setValuationFactors("{\"baseline\":755227,\"factors\":["
            + "{\"f\":\"living_area\",\"pct\":12.345,\"usd\":90123.40},"
            + "{\"f\":\"location\",\"pct\":-4.6,\"usd\":-37000},"
            + "{\"f\":\"new_factor\",\"pct\":0,\"usd\":0},"
            + "{\"f\":\"rooms\",\"usd\":1500},"
            + "{\"pct\":3}]}");
        p.setRunId("run-20261001");
        p.setInputHash("abcdef0123456789");
        return p;
    }

    private ComparableResponse comparables(String status) {
        ComparableResponse response = new ComparableResponse();
        response.setStatus(status);
        return response;
    }

    @Nested
    @DisplayName("Input and lookup")
    class Lookup {
        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("When the ID is not positive, then it is rejected before any lookup")
        void whenIdNotPositive_thenRejected(long id) {
            assertThatThrownBy(() -> service.getValuation(id))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("propertyId must be positive");
            verify(propertyRepository, never()).existsById(anyLong());
            verifyNoInteractions(predictions, comparableService);
        }

        @Test
        @DisplayName("When the ID is null, then it is rejected")
        void whenIdNull_thenRejected() {
            assertThatThrownBy(() -> service.getValuation(null)).isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(predictions, comparableService);
        }

        @Test
        @DisplayName("When the property does not exist, then PropertyNotFoundException is thrown")
        void whenPropertyMissing_thenNotFound() {
            when(propertyRepository.existsById(ID)).thenReturn(false);
            assertThatThrownBy(() -> service.getValuation(ID))
                .isInstanceOf(PropertyNotFoundException.class).hasMessageContaining(String.valueOf(ID));
            verifyNoInteractions(predictions, comparableService);
        }

        @Test
        @DisplayName("When the property has no prediction, then it is unsupported/not_modelled without method or comparables")
        void whenNoPrediction_thenNotModelled() {
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.empty());

            ValuationResponse response = service.getValuation(ID);

            assertThat(response.status()).isEqualTo("unsupported");
            assertThat(response.reasons()).containsExactly(
                new Reason("not_modelled", "This property type is not covered by the model yet."));
            assertThat(response.method()).isNull();
            assertThat(response.estimate()).isNull();
            assertThat(response.evidence()).isNull();
            verifyNoInteractions(comparableService);
        }
    }

    @Nested
    @DisplayName("Unsupported results")
    class Unsupported {
        @Test
        @DisplayName("When stored status is unsupported with reasons, then only status, reasons and method are returned")
        void whenUnsupportedWithReasons_thenReasonsAndMethodOnly() {
            PropertyValuePrediction p = supported();
            p.setValuationStatus("unsupported");
            p.setStatusReasons("[\"no_living_area\",\"low_confidence\"]");
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(p));

            ValuationResponse response = service.getValuation(ID);

            assertThat(response.status()).isEqualTo("unsupported");
            assertThat(response.reasons()).extracting(Reason::code).containsExactly("no_living_area", "low_confidence");
            assertThat(response.method().modelVersion()).isEqualTo("2026.10");
            assertThat(response.estimate()).isNull();
            assertThat(response.forecasts()).isNull();
            assertThat(response.explanation()).isNull();
            assertThat(response.evidence()).isNull();
            assertThat(response.reproducibility()).isNull();
            assertThat(response.notes()).isNull();
            verifyNoInteractions(comparableService);
        }

        @Test
        @DisplayName("When stored status is unsupported without reasons, then no_estimate is added")
        void whenUnsupportedWithoutReasons_thenNoEstimate() {
            PropertyValuePrediction p = supported();
            p.setValuationStatus("unsupported");
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(p));

            assertThat(service.getValuation(ID).reasons()).extracting(Reason::code).containsExactly("no_estimate");
        }

        @Test
        @DisplayName("When a supported row has no predicted price, then it is unsupported with no_estimate appended")
        void whenSupportedWithoutPrice_thenUnsupported() {
            PropertyValuePrediction p = supported();
            p.setStatusReasons("[\"no_sale_history\"]");
            p.setPredictedPrice(null);
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(p));

            ValuationResponse response = service.getValuation(ID);

            assertThat(response.status()).isEqualTo("unsupported");
            assertThat(response.reasons()).extracting(Reason::code).containsExactly("no_sale_history", "no_estimate");
            verifyNoInteractions(comparableService);
        }

        @Test
        @DisplayName("When the stored status is unknown, then it is unsupported and the raw status is reported as a reason")
        void whenUnknownStatus_thenUnsupported() {
            PropertyValuePrediction p = supported();
            p.setValuationStatus("experimental");
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(p));

            ValuationResponse response = service.getValuation(ID);

            assertThat(response.status()).isEqualTo("unsupported");
            assertThat(response.reasons()).containsExactly(new Reason("experimental", "experimental"),
                new Reason("no_estimate", "The model could not value this property."));
            assertThat(response.estimate()).isNull();
        }

        @Test
        @DisplayName("When the stored status is null, then it is unsupported with no_estimate only")
        void whenNullStatus_thenUnsupported() {
            PropertyValuePrediction p = supported();
            p.setValuationStatus(null);
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(p));

            ValuationResponse response = service.getValuation(ID);

            assertThat(response.status()).isEqualTo("unsupported");
            assertThat(response.reasons()).extracting(Reason::code).containsExactly("no_estimate");
        }
    }

    @Nested
    @DisplayName("Supported and limited results")
    class Supported {
        @Test
        @DisplayName("When supported, then stored values are returned unchanged with ranges, forecasts, method and run references")
        void whenSupported_thenStoredValuesUnchanged() {
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(supported()));
            when(comparableService.findComparables(eq(ID), any())).thenReturn(comparables("COMPARABLES_FOUND"));

            ValuationResponse response = service.getValuation(ID);

            assertThat(response.status()).isEqualTo("supported");
            assertThat(response.reasons()).isEmpty();
            assertThat(response.estimate().value()).isEqualTo(new BigDecimal("822855.47"));
            assertThat(response.estimate().range80().low()).isEqualTo(new BigDecimal("676666.91"));
            assertThat(response.estimate().range80().high()).isEqualTo(new BigDecimal("1000626.92"));
            assertThat(response.estimate().range90().low()).isEqualTo(new BigDecimal("612063.54"));
            assertThat(response.estimate().range90().high()).isEqualTo(new BigDecimal("1106243.20"));
            assertThat(response.estimate().confidence()).isEqualTo("high");
            assertThat(response.forecasts().oneYear().value()).isEqualTo(new BigDecimal("844881.25"));
            assertThat(response.forecasts().threeYears().high()).isEqualTo(new BigDecimal("2983865.44"));
            assertThat(response.forecasts().sevenYears().low()).isEqualTo(new BigDecimal("226584.78"));
            assertThat(response.method().valuationMethod()).isEqualTo("gbm_hedonic");
            assertThat(response.method().predictionDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(response.method().dataThrough()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(response.method().createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 2, 3, 0));
            assertThat(response.reproducibility().runId()).isEqualTo("run-20261001");
            assertThat(response.reproducibility().inputHash()).isEqualTo("abcdef0123456789");
            assertThat(response.notes()).containsExactly("Stored model note.", FORECAST_NOTE);
        }

        @Test
        @DisplayName("When supported, then the summary shows the baseline and estimate in whole dollars")
        void whenSupported_thenSummaryUsesWholeDollars() {
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(supported()));
            when(comparableService.findComparables(eq(ID), any())).thenReturn(comparables("COMPARABLES_FOUND"));

            var explanation = service.getValuation(ID).explanation();

            assertThat(explanation.baseline()).isEqualByComparingTo("755227");
            assertThat(explanation.summary()).isEqualTo("A typical Miami-Dade home is valued at about $755,227 today. "
                + "This property is estimated at $822,855.");
            assertThat(explanation.note()).contains("supporting evidence");
        }

        @Test
        @DisplayName("When factors are stored, then exact values are kept, labels and texts are added and invalid entries are skipped")
        void whenFactorsStored_thenMapped() {
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(supported()));
            when(comparableService.findComparables(eq(ID), any())).thenReturn(comparables("COMPARABLES_FOUND"));

            List<Factor> factors = service.getValuation(ID).explanation().factors();

            assertThat(factors).extracting(Factor::code).containsExactly("living_area", "location", "new_factor", "rooms");
            Factor living = factors.get(0);
            assertThat(living.label()).isEqualTo("Living area");
            assertThat(living.pct()).isEqualTo(new BigDecimal("12.345"));
            assertThat(living.usd()).isEqualByComparingTo("90123.40");
            assertThat(living.text()).isEqualTo("Its living area raises the value by about 12%.");
            assertThat(factors.get(1).text()).isEqualTo("Its location lowers the value by about 5%.");
            assertThat(factors.get(2).label()).isEqualTo("new_factor");
            assertThat(factors.get(2).text()).isEqualTo("The new_factor factor has no percentage effect on the value.");
            assertThat(factors.get(3).pct()).isNull();
            assertThat(factors.get(3).text()).isEqualTo("Bedrooms and bathrooms: percentage effect is unavailable.");
        }

        @Test
        @DisplayName("When limited, then stored reasons are returned as caveats with the estimate")
        void whenLimited_thenReasonsAreCaveats() {
            PropertyValuePrediction p = supported();
            p.setValuationStatus("limited");
            p.setStatusReasons("[\"high_value_segment\",\"future_code\"]");
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(p));
            when(comparableService.findComparables(eq(ID), any())).thenReturn(comparables("COMPARABLES_FOUND"));

            ValuationResponse response = service.getValuation(ID);

            assertThat(response.status()).isEqualTo("limited");
            assertThat(response.estimate().value()).isEqualTo(new BigDecimal("822855.47"));
            assertThat(response.reasons()).containsExactly(
                new Reason("high_value_segment", "High-value properties have wider errors (about 33% median in testing)."),
                new Reason("future_code", "future_code"));
        }

        @Test
        @DisplayName("When there is no one-year forecast, then forecasts and the forecast note are omitted")
        void whenNoForecast_thenForecastsOmitted() {
            PropertyValuePrediction p = supported();
            p.setForecast1y(null);
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(p));
            when(comparableService.findComparables(eq(ID), any())).thenReturn(comparables("COMPARABLES_FOUND"));

            ValuationResponse response = service.getValuation(ID);

            assertThat(response.forecasts()).isNull();
            assertThat(response.notes()).containsExactly("Stored model note.");
        }

        @Test
        @DisplayName("When factors are missing, then the summary has no baseline and a note says factors are unavailable")
        void whenFactorsMissing_thenNote() {
            PropertyValuePrediction p = supported();
            p.setValuationFactors(null);
            p.setNotes(null);
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(p));
            when(comparableService.findComparables(eq(ID), any())).thenReturn(comparables("COMPARABLES_FOUND"));

            ValuationResponse response = service.getValuation(ID);

            assertThat(response.explanation().baseline()).isNull();
            assertThat(response.explanation().factors()).isEmpty();
            assertThat(response.explanation().summary()).isEqualTo("This property is estimated at $822,855.");
            assertThat(response.notes()).containsExactly(FORECAST_NOTE, "Model explanation factors are unavailable.");
        }

        @Test
        @DisplayName("When stored JSON is invalid or of the wrong shape, then it is ignored instead of failing the request")
        void whenStoredJsonInvalid_thenIgnored() {
            PropertyValuePrediction p = supported();
            p.setStatusReasons("not json");
            p.setNotes("{\"note\":\"object, not array\"}");
            p.setValuationFactors("{broken");
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(p));
            when(comparableService.findComparables(eq(ID), any())).thenReturn(comparables("COMPARABLES_FOUND"));

            ValuationResponse response = service.getValuation(ID);

            assertThat(response.status()).isEqualTo("supported");
            assertThat(response.reasons()).isEmpty();
            assertThat(response.explanation().factors()).isEmpty();
            assertThat(response.notes()).containsExactly(FORECAST_NOTE, "Model explanation factors are unavailable.");
        }

        @Test
        @DisplayName("When stored arrays contain non-string entries, then only the strings are kept")
        void whenNonStringEntries_thenSkipped() {
            PropertyValuePrediction p = supported();
            p.setStatusReasons("[\"low_confidence\", 7, null]");
            p.setValuationStatus("limited");
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(p));
            when(comparableService.findComparables(eq(ID), any())).thenReturn(comparables("COMPARABLES_FOUND"));

            assertThat(service.getValuation(ID).reasons()).extracting(Reason::code).containsExactly("low_confidence");
        }

        @Test
        @DisplayName("When the estimate ends in 50 cents, then the summary rounds half up")
        void whenHalfDollar_thenRoundsUp() {
            PropertyValuePrediction p = supported();
            p.setPredictedPrice(new BigDecimal("822855.50"));
            p.setValuationFactors(null);
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(p));
            when(comparableService.findComparables(eq(ID), any())).thenReturn(comparables("COMPARABLES_FOUND"));

            assertThat(service.getValuation(ID).explanation().summary()).isEqualTo("This property is estimated at $822,856.");
        }
    }

    @Nested
    @DisplayName("Evidence")
    class EvidenceTest {
        @Test
        @DisplayName("When supported, then the last sale is returned and 5 supporting comparables are requested separately")
        void whenSupported_thenEvidence() {
            ComparableResponse found = comparables("COMPARABLES_FOUND");
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(supported()));
            when(comparableService.findComparables(eq(ID), any())).thenReturn(found);

            var evidence = service.getValuation(ID).evidence();

            assertThat(evidence.lastSale().hasSaleHistory()).isTrue();
            assertThat(evidence.lastSale().price()).isEqualTo(new BigDecimal("610000.00"));
            assertThat(evidence.lastSale().date()).isEqualTo(LocalDate.of(2019, 5, 2));
            assertThat(evidence.comparables().status()).isEqualTo("COMPARABLES_FOUND");
            assertThat(evidence.comparables().data()).isSameAs(found);
            assertThat(evidence.comparables().label()).contains("not the model's calculation");
            verify(comparableService).findComparables(eq(ID), argThat(request -> request.getLimit() == 5));
        }

        @Test
        @DisplayName("When comparables report insufficient data, then their status is passed through")
        void whenInsufficient_thenStatusPassedThrough() {
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(supported()));
            when(comparableService.findComparables(eq(ID), any())).thenReturn(comparables("INSUFFICIENT_DATA"));

            assertThat(service.getValuation(ID).evidence().comparables().status()).isEqualTo("INSUFFICIENT_DATA");
        }

        @Test
        @DisplayName("When the comparables query fails in the database, then the valuation is still returned with comparables UNAVAILABLE")
        void whenDatabaseFails_thenUnavailable() {
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(supported()));
            when(comparableService.findComparables(eq(ID), any()))
                .thenThrow(new DataAccessResourceFailureException("connection reset"));

            ValuationResponse response = service.getValuation(ID);

            assertThat(response.status()).isEqualTo("supported");
            assertThat(response.evidence().comparables().status()).isEqualTo("UNAVAILABLE");
            assertThat(response.evidence().comparables().data()).isNull();
        }

        @Test
        @DisplayName("When the comparables transaction times out, then comparables are UNAVAILABLE")
        void whenTimeout_thenUnavailable() {
            when(predictions.findByPropertyId(ID)).thenReturn(Optional.of(supported()));
            when(comparableService.findComparables(eq(ID), any()))
                .thenThrow(new TransactionTimedOutException("timed out"));

            assertThat(service.getValuation(ID).evidence().comparables().status()).isEqualTo("UNAVAILABLE");
        }
    }
}
