package com.geointelli.ai.property.service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.PriceDivergenceDTO;
import com.geointelli.ai.property.service.dto.PriceDivergenceDTO.Benchmark;
import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PriceDivergenceReader;
import com.geointelli.ai.property.service.repository.PriceDivergenceReader.GroupValuations;
import com.geointelli.ai.property.service.repository.PriceDivergenceReader.Level;
import com.geointelli.ai.property.service.repository.PriceDivergenceReader.MemberFactors;
import com.geointelli.ai.property.service.service.ValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PriceDivergenceServiceImplTest {

    PriceDivergenceReader reader = mock(PriceDivergenceReader.class);
    ValuationService valuationService = mock(ValuationService.class);
    PriceDivergenceServiceImpl service = new PriceDivergenceServiceImpl(reader, valuationService, new ObjectMapper());

    private static final Long PROPERTY_ID = 1542L;
    private static final PriceDivergenceReader.Subject SUBJECT = new PriceDivergenceReader.Subject(
            PROPERTY_ID, "0141390012345", 1L, 900L, "CORAL GARDENS SUB", "3200", new BigDecimal("1000"));
    private static final LocalDate OCT_1 = LocalDate.parse("2026-10-01");
    private static final LocalDate NOV_10 = LocalDate.parse("2026-11-10");
    private static final String GROUP_FACTORS = "{\"baseline\":400000,\"factors\":[{\"f\":\"living_area\",\"usd\":45000},"
            + "{\"f\":\"location\",\"usd\":38000},{\"f\":\"other\",\"usd\":-3000}]}";

    private static ValuationResponse.Factor factor(String code, String label, String usd) {
        return new ValuationResponse.Factor(code, label, null, new BigDecimal(usd), "");
    }

    private static ValuationResponse valuation(String value, List<ValuationResponse.Factor> factors) {
        return new ValuationResponse("supported", List.of(),
                new ValuationResponse.Estimate(new BigDecimal(value), null, null, "medium"), null,
                new ValuationResponse.Explanation(new BigDecimal("400000"), "", factors, ""), null,
                new ValuationResponse.Method("model", "v1", NOV_10, NOV_10, null), null, List.of());
    }

    private static MemberFactors member(String estimate, String json) {
        return new MemberFactors(new BigDecimal(estimate), json);
    }

    /** Members whose estimate is exactly baseline 400,000 plus their factors (80,000). */
    private static List<MemberFactors> rows(int count, String json) {
        List<MemberFactors> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(member("480000", json));
        }
        return rows;
    }

    @BeforeEach
    void setUp() {
        when(reader.findSubject(PROPERTY_ID)).thenReturn(Optional.of(SUBJECT));
        when(valuationService.getValuationWithoutComparables(PROPERTY_ID)).thenReturn(valuation("520000", List.of(
                factor("living_area", "Living area", "60000"), factor("location", "Location", "40000"))));
        when(reader.groupValuations(eq(Level.BUILDING), any()))
                .thenReturn(new GroupValuations(3, null, null, null, OCT_1, OCT_1));
        when(reader.groupValuations(eq(Level.COMMUNITY), any())).thenReturn(new GroupValuations(12,
                new BigDecimal("480000"), new BigDecimal("490000.004"), new BigDecimal("480.123"), OCT_1, NOV_10));
        when(reader.groupValuations(eq(Level.MICRO_MARKET), any())).thenReturn(new GroupValuations(47,
                new BigDecimal("450000"), new BigDecimal("455000"), new BigDecimal("450"), OCT_1, NOV_10));
        List<MemberFactors> factors = rows(6, GROUP_FACTORS);
        // unreadable factors: neither its factors nor its estimate enter the averages
        factors.add(member("9999999", "not json"));
        when(reader.groupValuationFactors(eq(Level.COMMUNITY), any())).thenReturn(factors);
    }

    @Nested
    @DisplayName("Test Benchmarks")
    class BenchmarksTest {

        @Test
        @DisplayName("When groups have enough valued properties, then the divergence from each median is returned")
        void whenEnoughEvidence_thenDivergenceIsReturned() {
            // Act
            PriceDivergenceDTO dto = service.getDivergence(PROPERTY_ID);

            // Assert
            assertNotNull(dto.observedAt());
            assertEquals("0141390012345", dto.folio());
            assertEquals(new BigDecimal("520.00"), dto.subject().pricePerSqft());
            assertEquals(NOV_10, dto.subject().valuationDate());
            Benchmark community = dto.benchmarks().get(1);
            assertEquals(PriceDivergenceDTO.AVAILABLE, community.status());
            assertEquals("CORAL GARDENS SUB", community.groupKey());
            assertEquals(12, community.sampleSize());
            assertEquals(new BigDecimal("480000.00"), community.medianValue());
            assertEquals(new BigDecimal("490000.00"), community.averageValue());
            assertEquals(new BigDecimal("480.12"), community.medianPricePerSqft());
            assertEquals(new BigDecimal("40000.00"), community.divergenceUsd());
            assertEquals(new BigDecimal("8.33"), community.divergencePct());
            assertEquals(OCT_1, community.valuationsFrom());
            assertEquals(NOV_10, community.valuationsTo());
            assertEquals(new BigDecimal("15.56"), dto.benchmarks().get(2).divergencePct());
        }

        @Test
        @DisplayName("When a group has fewer than 5 valued properties, then it is unavailable without invented values")
        void whenSmallSample_thenUnavailable() {
            // Act
            Benchmark building = service.getDivergence(PROPERTY_ID).benchmarks().get(0);

            // Assert
            assertEquals(PriceDivergenceDTO.UNAVAILABLE, building.status());
            assertEquals(3, building.sampleSize());
            assertNull(building.medianValue());
            assertNull(building.averageValue());
            assertNull(building.divergenceUsd());
            assertNull(building.divergencePct());
            assertEquals("Only 3 other valued properties in this group (minimum 5)", building.reason());
        }

        @Test
        @DisplayName("When the most specific level is unavailable, then the next available level is primary")
        void whenBuildingUnavailable_thenCommunityIsPrimary() {
            // Act
            PriceDivergenceDTO dto = service.getDivergence(PROPERTY_ID);

            // Assert
            assertEquals("COMMUNITY", dto.primaryBenchmark());
        }

        @Test
        @DisplayName("When the property has no group keys, then every level is unavailable and no group is read")
        void whenNoKeys_thenUnavailableWithoutQuery() {
            // Arrange
            when(reader.findSubject(PROPERTY_ID)).thenReturn(Optional.of(
                    new PriceDivergenceReader.Subject(PROPERTY_ID, "F", 1L, null, null, null, null)));

            // Act
            PriceDivergenceDTO dto = service.getDivergence(PROPERTY_ID);

            // Assert
            dto.benchmarks().forEach(b -> assertEquals(PriceDivergenceDTO.UNAVAILABLE, b.status()));
            assertEquals("The property is not a unit of a building", dto.benchmarks().get(0).reason());
            assertNull(dto.primaryBenchmark());
            assertNull(dto.subject().pricePerSqft());
            verify(reader, never()).groupValuations(any(), any());
        }

        @Test
        @DisplayName("When the property has no estimate, then medians are shown without a divergence")
        void whenNoEstimate_thenNoDivergence() {
            // Arrange
            when(valuationService.getValuationWithoutComparables(PROPERTY_ID)).thenReturn(
                    new ValuationResponse("unsupported", List.of(), null, null, null, null, null, null, null));

            // Act
            PriceDivergenceDTO dto = service.getDivergence(PROPERTY_ID);

            // Assert
            assertNull(dto.subject().estimate());
            assertEquals(new BigDecimal("480000.00"), dto.benchmarks().get(1).medianValue());
            assertNull(dto.benchmarks().get(1).divergenceUsd());
            assertEquals("The property has no estimate to compare", dto.benchmarks().get(1).reason());
            assertEquals(PriceDivergenceDTO.UNAVAILABLE, dto.drivers().status());
            assertEquals(PriceDivergenceDTO.UNAVAILABLE, dto.confidence().level());
        }
    }

    @Nested
    @DisplayName("Test Drivers And Residual")
    class DriversTest {

        @Test
        @DisplayName("When factors are readable, then drivers are differences from the group average, largest first")
        void whenFactorsReadable_thenDifferencesAreReturned() {
            // Act
            PriceDivergenceDTO.Drivers drivers = service.getDivergence(PROPERTY_ID).drivers();

            // Assert
            assertEquals(PriceDivergenceDTO.AVAILABLE, drivers.status());
            assertEquals("COMMUNITY", drivers.comparedWith());
            assertEquals(6, drivers.sampleSize());
            assertEquals(new BigDecimal("480000.00"), drivers.groupAverageValue());
            assertEquals(new BigDecimal("40000.00"), drivers.divergenceFromAverageUsd());
            assertEquals("living_area", drivers.factors().get(0).code());
            assertEquals(new BigDecimal("60000.00"), drivers.factors().get(0).subjectUsd());
            assertEquals(new BigDecimal("45000.00"), drivers.factors().get(0).groupAverageUsd());
            assertEquals(new BigDecimal("15000.00"), drivers.factors().get(0).differenceUsd());
            assertEquals("other", drivers.factors().get(1).code());
            assertEquals(new BigDecimal("0.00"), drivers.factors().get(1).subjectUsd());
            assertEquals(new BigDecimal("3000.00"), drivers.factors().get(1).differenceUsd());
            assertEquals(new BigDecimal("2000.00"), drivers.factors().get(2).differenceUsd());
            assertEquals(new BigDecimal("20000.00"), drivers.explainedUsd());
        }

        @Test
        @DisplayName("When drivers are available, then the residual is the gap from the group average minus the explained amount")
        void whenDriversAvailable_thenResidualIsCalculated() {
            // Act
            PriceDivergenceDTO dto = service.getDivergence(PROPERTY_ID);

            // Assert: the property is 20,000 above baseline plus its factors; the members are exactly on it
            assertEquals(PriceDivergenceDTO.AVAILABLE, dto.unexplainedResidual().status());
            assertEquals("COMMUNITY", dto.unexplainedResidual().benchmark());
            assertEquals(new BigDecimal("20000.00"), dto.unexplainedResidual().usd());
            assertEquals("MEDIUM", dto.confidence().level());
            assertTrue(dto.confidence().reason().contains("12"));
        }

        @Test
        @DisplayName("When a few members are much higher than the rest, then the residual does not include the median-to-average gap")
        void whenGroupSkewed_thenResidualExcludesMedianAverageGap() {
            // Arrange: subject 500,000 = 400,000 + 60,000 + 40,000 exactly; four members at 480,000 and one at
            // 980,000, each exactly baseline plus its factors; median 480,000, average 580,000
            when(valuationService.getValuationWithoutComparables(PROPERTY_ID)).thenReturn(valuation("500000", List.of(
                    factor("living_area", "Living area", "60000"), factor("location", "Location", "40000"))));
            when(reader.groupValuations(eq(Level.COMMUNITY), any())).thenReturn(new GroupValuations(5,
                    new BigDecimal("480000"), new BigDecimal("580000"), null, OCT_1, OCT_1));
            List<MemberFactors> factors = rows(4, GROUP_FACTORS);
            factors.add(member("980000", "{\"baseline\":400000,\"factors\":[{\"f\":\"living_area\",\"usd\":545000},"
                    + "{\"f\":\"location\",\"usd\":38000},{\"f\":\"other\",\"usd\":-3000}]}"));
            when(reader.groupValuationFactors(eq(Level.COMMUNITY), any())).thenReturn(factors);

            // Act
            PriceDivergenceDTO dto = service.getDivergence(PROPERTY_ID);

            // Assert
            Benchmark community = dto.benchmarks().get(1);
            assertEquals(new BigDecimal("20000.00"), community.divergenceUsd());
            assertEquals(new BigDecimal("580000.00"), community.averageValue());
            assertEquals(new BigDecimal("580000.00"), dto.drivers().groupAverageValue());
            assertEquals(new BigDecimal("-80000.00"), dto.drivers().divergenceFromAverageUsd());
            assertEquals(new BigDecimal("-80000.00"), dto.drivers().explainedUsd());
            assertEquals(new BigDecimal("0.00"), dto.unexplainedResidual().usd());
        }

        @Test
        @DisplayName("When fewer than 5 group members have readable factors, then drivers and residual are unavailable")
        void whenFewFactors_thenUnavailable() {
            // Arrange
            List<MemberFactors> factors = rows(4, GROUP_FACTORS);
            factors.add(member("480000", "not json"));
            factors.add(member("480000", "{\"baseline\":400000}"));
            when(reader.groupValuationFactors(eq(Level.COMMUNITY), any())).thenReturn(factors);

            // Act
            PriceDivergenceDTO dto = service.getDivergence(PROPERTY_ID);

            // Assert
            assertEquals(PriceDivergenceDTO.UNAVAILABLE, dto.drivers().status());
            assertEquals(4, dto.drivers().sampleSize());
            assertTrue(dto.drivers().factors().isEmpty());
            assertNull(dto.drivers().explainedUsd());
            assertNull(dto.drivers().groupAverageValue());
            assertNull(dto.drivers().divergenceFromAverageUsd());
            assertEquals(PriceDivergenceDTO.UNAVAILABLE, dto.unexplainedResidual().status());
            assertNull(dto.unexplainedResidual().usd());
        }

        @Test
        @DisplayName("When the property has no explanation factors, then drivers are unavailable")
        void whenNoSubjectFactors_thenUnavailable() {
            // Arrange
            when(valuationService.getValuationWithoutComparables(PROPERTY_ID)).thenReturn(valuation("520000", List.of()));

            // Act
            PriceDivergenceDTO dto = service.getDivergence(PROPERTY_ID);

            // Assert
            assertEquals(PriceDivergenceDTO.UNAVAILABLE, dto.drivers().status());
            assertEquals("Model explanation factors are unavailable for the property", dto.drivers().reason());
            verify(reader, never()).groupValuationFactors(any(), any());
        }

        @Test
        @DisplayName("When no group has enough evidence, then drivers, residual and confidence are unavailable")
        void whenNoBenchmark_thenEverythingUnavailable() {
            // Arrange
            when(reader.groupValuations(any(), any())).thenReturn(new GroupValuations(2, null, null, null, OCT_1, OCT_1));

            // Act
            PriceDivergenceDTO dto = service.getDivergence(PROPERTY_ID);

            // Assert
            assertNull(dto.primaryBenchmark());
            assertEquals(PriceDivergenceDTO.UNAVAILABLE, dto.drivers().status());
            assertEquals(PriceDivergenceDTO.UNAVAILABLE, dto.unexplainedResidual().status());
            assertEquals(PriceDivergenceDTO.UNAVAILABLE, dto.confidence().level());
        }

        @Test
        @DisplayName("The asking-price comparison is always separate and unavailable")
        void askingPriceIsUnavailable() {
            // Act
            PriceDivergenceDTO.AskingPriceComparison asking = service.getDivergence(PROPERTY_ID).askingPriceComparison();

            // Assert
            assertEquals(PriceDivergenceDTO.UNAVAILABLE, asking.status());
            assertNull(asking.askingPrice());
            assertEquals("No asking-price data is recorded", asking.reason());
        }
    }

    @Nested
    @DisplayName("Test Invalid Input")
    class InvalidInputTest {

        @Test
        @DisplayName("When the ID is not positive, then IllegalArgumentException is thrown")
        void whenInvalidId_thenThrow() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> service.getDivergence(0L));
            assertThrows(IllegalArgumentException.class, () -> service.getDivergence(null));
            verifyNoInteractions(reader, valuationService);
        }

        @Test
        @DisplayName("When the property does not exist, then PropertyNotFoundException is thrown")
        void whenUnknown_thenThrow() {
            // Arrange
            when(reader.findSubject(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(PropertyNotFoundException.class, () -> service.getDivergence(99L));
            verifyNoInteractions(valuationService);
        }
    }
}
