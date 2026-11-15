package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.OpportunityScoreDTO;
import com.geointelli.ai.property.service.dto.OpportunityScoreDTO.Factor;
import com.geointelli.ai.property.service.dto.OpportunitySignals;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.OpportunityScoreJdbcRepository;
import com.geointelli.ai.property.service.service.OpportunityScoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpportunityScoringServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    private final OpportunityScoreJdbcRepository repository = mock(OpportunityScoreJdbcRepository.class);
    private final OpportunityScoringServiceImpl service =
            new OpportunityScoringServiceImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    /** 300 000 estimate, +4% in one year, +10% in three, outlook -0.17%, confidence 85. */
    private static OpportunitySignals complete() {
        return new OpportunitySignals(95340L, 1L, 10L, true, "supported", new BigDecimal("300000.00"),
                new BigDecimal("312000.00"), new BigDecimal("330000.00"), -0.17, "high", 85, "v17+v18",
                LocalDate.of(2026, 7, 26), "2026-11-13-tecdebe94");
    }

    private static OpportunitySignals with(UnaryOperator<Builder> change) {
        return change.apply(new Builder(complete())).build();
    }

    private static Factor factor(OpportunityScoreDTO score, String code) {
        return score.factors().stream().filter(factor -> factor.code().equals(code)).findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("Score")
    class Score {

        @Test
        @DisplayName("With complete stored evidence, every available factor adds weight x subscore / 100 points")
        void completeEvidence_isScoredFromTheFourAvailableFactors() {
            OpportunityScoreDTO score = service.score(complete(), NOW);

            assertEquals(OpportunityScoreDTO.STATUS_SCORED, score.status());
            assertEquals(OpportunityScoringService.SCORE_VERSION, score.scoreVersion());
            assertEquals(100, score.maxScore());
            // 1y +4% -> 70 x 20 = 14.00; 3y +10% -> 70 x 15 = 10.50; outlook -0.17% -> 49.15 x 5 = 2.46; 85 x 10 = 8.50
            assertEquals(new BigDecimal("14.00"), factor(score, "FORECAST_GROWTH_1Y").points());
            assertEquals(new BigDecimal("4.00"), factor(score, "FORECAST_GROWTH_1Y").signal());
            assertEquals(new BigDecimal("70.00"), factor(score, "FORECAST_GROWTH_1Y").subscore());
            assertEquals(new BigDecimal("10.50"), factor(score, "FORECAST_GROWTH_3Y").points());
            assertEquals(new BigDecimal("2.46"), factor(score, "MARKET_OUTLOOK").points());
            assertEquals(new BigDecimal("8.50"), factor(score, "VALUATION_CONFIDENCE").points());
            assertEquals(new BigDecimal("35.46"), score.score());
            assertEquals(new BigDecimal("50.00"), score.coveragePercent());
            assertTrue(score.reasons().isEmpty());
            assertEquals(NOW, score.scoredAt());
            assertEquals("2026-11-13-tecdebe94", score.valuation().runId());
            assertEquals("v17+v18", score.valuation().modelVersion());
            assertEquals(OpportunityScoringService.NOTES, score.notes());
        }

        @Test
        @DisplayName("All seven factors are listed; weights add up to 100 and the score never exceeds the coverage")
        void everyFactorIsListed_andWeightsAddUpToTheMaximum() {
            OpportunityScoreDTO score = service.score(complete(), NOW);

            assertEquals(List.of("FORECAST_GROWTH_1Y", "FORECAST_GROWTH_3Y", "MARKET_OUTLOOK", "VALUATION_CONFIDENCE",
                    "RENTAL_YIELD", "FINANCED_CASH_FLOW", "ASKING_PRICE_DISCOUNT"),
                    score.factors().stream().map(Factor::code).toList());
            assertEquals(100, score.factors().stream().mapToInt(Factor::weight).sum());
            assertTrue(score.score().compareTo(score.coveragePercent()) <= 0);
        }

        @Test
        @DisplayName("Rent, financing and asking price are unavailable with a reason, add no points and are not a zero cost")
        void missingRentFinancingAndPrice_areUnavailableWithReasons() {
            OpportunityScoreDTO score = service.score(complete(), NOW);

            for (String code : List.of("RENTAL_YIELD", "FINANCED_CASH_FLOW", "ASKING_PRICE_DISCOUNT")) {
                Factor factor = factor(score, code);
                assertEquals("UNAVAILABLE", factor.status(), code);
                assertNull(factor.points(), code);
                assertNull(factor.subscore(), code);
                assertNull(factor.signal(), code);
                assertTrue(factor.explanation().startsWith("Unavailable: "), code);
                assertTrue(factor.explanation().endsWith("It adds no points."), code);
            }
            assertTrue(factor(score, "RENTAL_YIELD").explanation().contains("No rent estimate"));
            assertTrue(factor(score, "ASKING_PRICE_DISCOUNT").explanation().contains("No listing price"));
        }

        @Test
        @DisplayName("Removing any optional evidence never raises the score")
        void missingOptionalEvidence_neverImprovesTheScore() {
            BigDecimal complete = service.score(complete(), NOW).score();

            List<OpportunitySignals> partial = List.of(
                    with(b -> b.forecast3y(null)),
                    with(b -> b.marketOutlook(null)),
                    with(b -> b.confidenceScore(null)),
                    with(b -> b.forecast3y(null).marketOutlook(null).confidenceScore(null)));
            for (OpportunitySignals signals : partial) {
                OpportunityScoreDTO score = service.score(signals, NOW);
                assertEquals(OpportunityScoreDTO.STATUS_SCORED, score.status());
                assertTrue(score.score().compareTo(complete) < 0, () -> "missing evidence raised the score: " + score.score());
                assertTrue(score.coveragePercent().compareTo(new BigDecimal("50.00")) < 0);
            }
            OpportunityScoreDTO onlyOneYear = service.score(partial.get(3), NOW);
            assertEquals(new BigDecimal("14.00"), onlyOneYear.score());
            assertEquals(new BigDecimal("20.00"), onlyOneYear.coveragePercent());
        }

        @Test
        @DisplayName("Signals beyond the documented ranges are clamped to subscores 0 and 100")
        void extremeSignals_areClamped() {
            OpportunityScoreDTO high = service.score(with(b -> b.forecast1y(new BigDecimal("450000"))
                    .forecast3y(new BigDecimal("600000")).marketOutlook(40.0)), NOW);
            OpportunityScoreDTO low = service.score(with(b -> b.forecast1y(new BigDecimal("150000"))
                    .forecast3y(new BigDecimal("100000")).marketOutlook(-40.0)), NOW);

            for (String code : List.of("FORECAST_GROWTH_1Y", "FORECAST_GROWTH_3Y", "MARKET_OUTLOOK")) {
                assertEquals(new BigDecimal("100.00"), factor(high, code).subscore(), code);
                assertEquals(new BigDecimal("0.00"), factor(low, code).subscore(), code);
            }
            assertEquals(new BigDecimal("48.50"), high.score());
            assertEquals(new BigDecimal("8.50"), low.score());
        }

        @Test
        @DisplayName("A limited valuation is still scored; its confidence factor reflects it")
        void limitedValuation_isScored() {
            OpportunityScoreDTO score = service.score(with(b -> b.status("limited").confidence("low").confidenceScore(45)), NOW);

            assertEquals(OpportunityScoreDTO.STATUS_SCORED, score.status());
            assertEquals(new BigDecimal("4.50"), factor(score, "VALUATION_CONFIDENCE").points());
            assertTrue(factor(score, "VALUATION_CONFIDENCE").explanation().contains("low confidence"));
        }

        @Test
        @DisplayName("A non-finite market outlook is treated as missing")
        void nonFiniteOutlook_isUnavailable() {
            OpportunityScoreDTO score = service.score(with(b -> b.marketOutlook(Double.NaN)), NOW);

            assertEquals("UNAVAILABLE", factor(score, "MARKET_OUTLOOK").status());
        }
    }

    @Nested
    @DisplayName("Essential evidence")
    class EssentialEvidence {

        @Test
        @DisplayName("Without a valuation the score is unavailable, not a low number")
        void noValuation_isUnavailable() {
            OpportunityScoreDTO score = service.score(OpportunitySignals.withoutValuation(7L, 1L, 10L), NOW);

            assertEquals(OpportunityScoreDTO.STATUS_UNAVAILABLE, score.status());
            assertNull(score.score());
            assertNull(score.valuation());
            assertEquals(List.of("NO_VALUATION"), score.reasons().stream().map(OpportunityScoreDTO.Reason::code).toList());
            assertEquals(new BigDecimal("0.00"), score.coveragePercent());
            assertTrue(score.factors().stream().allMatch(factor -> "UNAVAILABLE".equals(factor.status())));
        }

        @ParameterizedTest
        @ValueSource(strings = {"unsupported", "something-else"})
        @DisplayName("An unsupported or unknown valuation status makes the score unavailable")
        void unsupportedValuation_isUnavailable(String status) {
            OpportunityScoreDTO score = service.score(with(b -> b.status(status)), NOW);

            assertEquals(OpportunityScoreDTO.STATUS_UNAVAILABLE, score.status());
            assertNull(score.score());
            assertEquals("VALUATION_UNSUPPORTED", score.reasons().get(0).code());
            assertEquals(status, score.valuation().valuationStatus());
            assertEquals("UNAVAILABLE", factor(score, "FORECAST_GROWTH_1Y").status());
        }

        @Test
        @DisplayName("A valuation without an estimate, or with a zero estimate, makes the score unavailable")
        void missingEstimate_isUnavailable() {
            for (BigDecimal value : new BigDecimal[]{null, BigDecimal.ZERO}) {
                OpportunityScoreDTO score = service.score(with(b -> b.value(value)), NOW);
                assertEquals(OpportunityScoreDTO.STATUS_UNAVAILABLE, score.status());
                assertEquals("NO_ESTIMATE", score.reasons().get(0).code());
            }
        }

        @Test
        @DisplayName("Without a one-year forecast the score is unavailable, though the other evidence is still shown")
        void missingOneYearForecast_isUnavailable() {
            OpportunityScoreDTO score = service.score(with(b -> b.forecast1y(null)), NOW);

            assertEquals(OpportunityScoreDTO.STATUS_UNAVAILABLE, score.status());
            assertNull(score.score());
            assertEquals("NO_FORECAST", score.reasons().get(0).code());
            assertEquals("AVAILABLE", factor(score, "FORECAST_GROWTH_3Y").status());
            assertEquals(new BigDecimal("30.00"), score.coveragePercent());
        }
    }

    @Nested
    @DisplayName("Get score")
    class GetScore {

        @Test
        @DisplayName("Scores the stored evidence of the property at the clock's time")
        void existingProperty_isScoredFromItsStoredEvidence() {
            when(repository.findSignals(95340L)).thenReturn(Optional.of(complete()));

            OpportunityScoreDTO score = service.getScore(95340L);

            assertEquals(95340L, score.propertyId());
            assertEquals(new BigDecimal("35.46"), score.score());
            assertEquals(NOW, score.scoredAt());
        }

        @Test
        @DisplayName("A property without a valuation returns an unavailable score")
        void propertyWithoutValuation_returnsUnavailable() {
            when(repository.findSignals(7L)).thenReturn(Optional.of(OpportunitySignals.withoutValuation(7L, null, null)));

            assertEquals(OpportunityScoreDTO.STATUS_UNAVAILABLE, service.getScore(7L).status());
        }

        @Test
        @DisplayName("An unknown property is not found")
        void unknownProperty_isNotFound() {
            when(repository.findSignals(999L)).thenReturn(Optional.empty());

            assertThrows(PropertyNotFoundException.class, () -> service.getScore(999L));
        }

        @Test
        @DisplayName("A missing or non-positive ID is rejected before reading")
        void invalidId_isRejected() {
            assertThrows(IllegalArgumentException.class, () -> service.getScore(null));
            assertThrows(IllegalArgumentException.class, () -> service.getScore(0L));
            assertThrows(IllegalArgumentException.class, () -> service.getScore(-5L));
            verifyNoInteractions(repository);
        }
    }

    /** Copies complete signals with some values changed. */
    private static final class Builder {
        private final OpportunitySignals base;
        private String status;
        private BigDecimal value;
        private BigDecimal forecast1y;
        private BigDecimal forecast3y;
        private Double outlook;
        private String confidence;
        private Integer confidenceScore;

        private Builder(OpportunitySignals base) {
            this.base = base;
            this.status = base.valuationStatus();
            this.value = base.estimatedValue();
            this.forecast1y = base.forecast1y();
            this.forecast3y = base.forecast3y();
            this.outlook = base.marketOutlook1yPct();
            this.confidence = base.confidence();
            this.confidenceScore = base.confidenceScore();
        }

        Builder status(String value) { this.status = value; return this; }
        Builder value(BigDecimal amount) { this.value = amount; return this; }
        Builder forecast1y(BigDecimal amount) { this.forecast1y = amount; return this; }
        Builder forecast3y(BigDecimal amount) { this.forecast3y = amount; return this; }
        Builder marketOutlook(Double pct) { this.outlook = pct; return this; }
        Builder confidence(String label) { this.confidence = label; return this; }
        Builder confidenceScore(Integer score) { this.confidenceScore = score; return this; }

        OpportunitySignals build() {
            return new OpportunitySignals(base.propertyId(), base.countyId(), base.stateId(), true, status, value,
                    forecast1y, forecast3y, outlook, confidence, confidenceScore, base.modelVersion(),
                    base.dataThrough(), base.valuationRunId());
        }
    }
}
