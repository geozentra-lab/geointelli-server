package com.geointelli.ai.property.service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.dto.OpportunityBrowseRequest;
import com.geointelli.ai.property.service.dto.OpportunityPageDTO;
import com.geointelli.ai.property.service.dto.OpportunityScoreDTO;
import com.geointelli.ai.property.service.entity.PropertyOpportunityScore;
import com.geointelli.ai.property.service.repository.PropertyOpportunityScoreRepository;
import com.geointelli.ai.property.service.repository.PropertyOpportunityScoreRepository.PropertyRow;
import com.geointelli.ai.property.service.service.OpportunityScoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpportunityBrowseServiceImplTest {

    private static final String VERSION = OpportunityScoringService.SCORE_VERSION;
    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private final PropertyOpportunityScoreRepository repository = mock(PropertyOpportunityScoreRepository.class);
    private final OpportunityBrowseServiceImpl service = new OpportunityBrowseServiceImpl(repository, JSON);

    private static OpportunityBrowseRequest request(String sort) {
        return new OpportunityBrowseRequest(0, 20, null, null, null, null, null, sort);
    }

    private static PropertyOpportunityScore stored(long propertyId, String score) {
        List<OpportunityScoreDTO.Factor> factors = List.of(
                new OpportunityScoreDTO.Factor("FORECAST_GROWTH_1Y", "One-year forecast growth", "MARKET", 20, "AVAILABLE",
                        new BigDecimal("4.00"), "PERCENT", new BigDecimal("70.00"), new BigDecimal("14.00"), "One-year ..."),
                new OpportunityScoreDTO.Factor("RENTAL_YIELD", "Rental yield", "RENT", 20, "UNAVAILABLE",
                        null, null, null, null, "Unavailable: no rent. It adds no points."));
        return new PropertyOpportunityScore(propertyId, VERSION, "SCORED", new BigDecimal(score), new BigDecimal("50.00"),
                JSON.valueToTree(factors), JSON.createArrayNode(), 1L, 10L, new BigDecimal("300000.00"),
                new BigDecimal("4.00"), "run-1", "v17+v18", LocalDate.of(2026, 7, 26),
                Instant.parse("2026-11-15T10:00:00Z"));
    }

    private static PropertyRow row(long id, String folio, String address) {
        PropertyRow row = mock(PropertyRow.class);
        when(row.getPropertyId()).thenReturn(id);
        when(row.getFolio()).thenReturn(folio);
        when(row.getAddress()).thenReturn(address);
        when(row.getCity()).thenReturn("Miami");
        when(row.getZip()).thenReturn("33101");
        when(row.getCountyName()).thenReturn("Miami-Dade");
        when(row.getStateCode()).thenReturn("FL");
        return row;
    }

    private void returns(List<PropertyOpportunityScore> rows, long total) {
        when(repository.browse(eq(VERSION), any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> new PageImpl<>(rows, call.getArgument(6, Pageable.class), total));
    }

    private Pageable captureBrowse() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).browse(eq(VERSION), any(), any(), any(), any(), any(), pageable.capture());
        return pageable.getValue();
    }

    @Nested
    @DisplayName("Results")
    class Results {

        @Test
        @DisplayName("Lists stored scores with their property details, factors and coverage")
        void page_listsScoresWithPropertyDetails() {
            returns(List.of(stored(2L, "40.10"), stored(1L, "35.46")), 2);
            PropertyRow second = row(2L, "0101", "1 Main St");
            when(repository.findPropertyRows(List.of(2L, 1L))).thenReturn(List.of(second));

            OpportunityPageDTO page = service.browse(request(null));

            assertEquals(OpportunityPageDTO.STATUS_OK, page.status());
            assertEquals(VERSION, page.scoreVersion());
            assertEquals("SCORE_DESC", page.sort());
            assertEquals(2, page.results().totalElements());
            OpportunityPageDTO.Item first = page.results().content().get(0);
            assertEquals(2L, first.propertyId());
            assertEquals("0101", first.folio());
            assertEquals("1 Main St", first.address());
            assertEquals("Miami-Dade", first.countyName());
            assertEquals("FL", first.stateCode());
            assertEquals(new BigDecimal("40.10"), first.score());
            assertEquals(100, first.maxScore());
            assertEquals(new BigDecimal("50.00"), first.coveragePercent());
            assertEquals("run-1", first.valuationRunId());
            assertEquals(2, first.factors().size());
            assertEquals(new BigDecimal("14.00"), first.factors().get(0).points());
            assertNull(first.factors().get(1).points());
            // A property row that is gone leaves the details empty instead of failing the page
            assertNull(page.results().content().get(1).folio());
            assertEquals(OpportunityScoringService.NOTES, page.notes());
        }

        @Test
        @DisplayName("Passes every filter to the repository and echoes them")
        void filters_arePassedAndEchoed() {
            returns(List.of(), 0);
            when(repository.existsByScoreVersion(VERSION)).thenReturn(true);
            when(repository.existsByScoreVersionAndStatus(VERSION, "SCORED")).thenReturn(true);

            OpportunityPageDTO page = service.browse(new OpportunityBrowseRequest(2, 5, 7L, 3L, new BigDecimal("30"),
                    new BigDecimal("100000"), new BigDecimal("500000"), "value_asc"));

            verify(repository).browse(eq(VERSION), eq(7L), eq(3L), eq(new BigDecimal("30")), eq(new BigDecimal("100000")),
                    eq(new BigDecimal("500000")), any(Pageable.class));
            assertEquals(new OpportunityPageDTO.Filters(7L, 3L, new BigDecimal("30"), new BigDecimal("100000"),
                    new BigDecimal("500000")), page.filters());
            assertEquals("VALUE_ASC", page.sort());
            assertEquals(2, page.results().page());
            assertEquals(5, page.results().size());
            verify(repository, never()).findPropertyRows(any());
        }
    }

    @Nested
    @DisplayName("Order")
    class Order {

        @ParameterizedTest
        @CsvSource({
                "SCORE_DESC, 'score: DESC,propertyId: ASC'",
                "FORECAST_GROWTH_1Y_DESC, 'forecastGrowth1yPct: DESC,score: DESC,propertyId: ASC'",
                "VALUE_ASC, 'estimatedValue: ASC,score: DESC,propertyId: ASC'",
                "VALUE_DESC, 'estimatedValue: DESC,score: DESC,propertyId: ASC'"})
        @DisplayName("Each supported order ends with the property ID so pages never overlap")
        void supportedOrders_areTranslated(String sort, String expected) {
            returns(List.of(stored(1L, "35.46")), 1);
            when(repository.findPropertyRows(any())).thenReturn(List.of());

            service.browse(request(sort));

            Sort applied = captureBrowse().getSort();
            assertEquals(expected, applied.toString().replace(", ", ","));
        }

        @Test
        @DisplayName("Without a sort the order is the highest score first")
        void defaultOrder_isScoreDescending() {
            returns(List.of(), 0);

            service.browse(request("  "));

            assertEquals(Sort.by(Sort.Order.desc("score"), Sort.Order.asc("propertyId")), captureBrowse().getSort());
        }
    }

    @Nested
    @DisplayName("Empty pages")
    class EmptyPages {

        @Test
        @DisplayName("No stored score of the current version: NOT_SCORED")
        void nothingStored_isNotScored() {
            returns(List.of(), 0);
            when(repository.existsByScoreVersion(VERSION)).thenReturn(false);

            assertEquals(OpportunityPageDTO.STATUS_NOT_SCORED, service.browse(request(null)).status());
        }

        @Test
        @DisplayName("Only unavailable scores stored: NO_ELIGIBLE_OPPORTUNITIES")
        void onlyUnavailableScores_isNoEligible() {
            returns(List.of(), 0);
            when(repository.existsByScoreVersion(VERSION)).thenReturn(true);
            when(repository.existsByScoreVersionAndStatus(VERSION, "SCORED")).thenReturn(false);

            assertEquals(OpportunityPageDTO.STATUS_NO_ELIGIBLE, service.browse(request(null)).status());
        }

        @Test
        @DisplayName("Scored properties exist but the filters exclude them: NO_MATCHES")
        void filtersExcludeEverything_isNoMatches() {
            returns(List.of(), 0);
            when(repository.existsByScoreVersion(VERSION)).thenReturn(true);
            when(repository.existsByScoreVersionAndStatus(VERSION, "SCORED")).thenReturn(true);

            OpportunityPageDTO page = service.browse(request(null));

            assertEquals(OpportunityPageDTO.STATUS_NO_MATCHES, page.status());
            assertTrue(page.results().content().isEmpty());
        }

        @Test
        @DisplayName("A page past the end is empty but keeps status OK and the total")
        void pagePastTheEnd_keepsTotal() {
            when(repository.browse(eq(VERSION), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(6, Pageable.class), 3));

            OpportunityPageDTO page = service.browse(new OpportunityBrowseRequest(9, 20, null, null, null, null, null, null));

            assertEquals(OpportunityPageDTO.STATUS_OK, page.status());
            assertEquals(3, page.results().totalElements());
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest
        @CsvSource(nullValues = "null", value = {
                "-1, 20, null, null, null, null, null, null, page must be 0 or greater",
                "0, 0, null, null, null, null, null, null, size must be between 1 and 100",
                "0, 101, null, null, null, null, null, null, size must be between 1 and 100",
                "0, 20, 0, null, null, null, null, null, countyId must be a positive number",
                "0, 20, null, -2, null, null, null, null, stateId must be a positive number",
                "0, 20, null, null, -1, null, null, null, minScore must be between 0 and 100",
                "0, 20, null, null, 100.5, null, null, null, minScore must be between 0 and 100",
                "0, 20, null, null, null, -1, null, null, minValue must be 0 or greater",
                "0, 20, null, null, null, null, -1, null, maxValue must be 0 or greater",
                "0, 20, null, null, null, 500, 100, null, minValue must not exceed maxValue",
                "0, 20, null, null, null, null, null, BEST, 'sort must be one of SCORE_DESC, FORECAST_GROWTH_1Y_DESC, VALUE_ASC, VALUE_DESC'",
                "100, 100, null, null, null, null, null, null, 'page is too large: only the first 10000 results can be paged; narrow the filters'",
                "500, 20, null, null, null, null, null, null, 'page is too large: only the first 10000 results can be paged; narrow the filters'",
                "2147483647, 2, null, null, null, null, null, null, 'page is too large: only the first 10000 results can be paged; narrow the filters'"})
        @DisplayName("Invalid parameters are rejected before reading")
        void invalidParameters_areRejected(int page, int size, Long countyId, Long stateId, BigDecimal minScore,
                                           BigDecimal minValue, BigDecimal maxValue, String sort, String message) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.browse(
                    new OpportunityBrowseRequest(page, size, countyId, stateId, minScore, minValue, maxValue, sort)));

            assertEquals(message, error.getMessage());
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("Boundary values are accepted")
        void boundaryValues_areAccepted() {
            returns(List.of(), 0);

            assertDoesNotThrow(() -> service.browse(new OpportunityBrowseRequest(0, 100, null, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, null)));
            assertDoesNotThrow(() -> service.browse(new OpportunityBrowseRequest(0, 1, null, null, new BigDecimal("100"),
                    null, null, null)));
            // The last pages that start within the first 10,000 results
            assertDoesNotThrow(() -> service.browse(new OpportunityBrowseRequest(99, 100, null, null, null, null, null,
                    null)));
            assertDoesNotThrow(() -> service.browse(new OpportunityBrowseRequest(9999, 1, null, null, null, null, null,
                    null)));
        }

        @Test
        @DisplayName("A missing request is rejected")
        void missingRequest_isRejected() {
            assertThrows(IllegalArgumentException.class, () -> service.browse(null));
        }
    }
}
