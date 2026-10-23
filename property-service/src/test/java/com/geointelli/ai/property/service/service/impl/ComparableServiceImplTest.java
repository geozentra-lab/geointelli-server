package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.geointelli.ai.property.service.dto.ComparableCandidateDTO;
import com.geointelli.ai.property.service.dto.ComparableCandidateRequest;
import com.geointelli.ai.property.service.dto.ComparableCandidateResponse;
import com.geointelli.ai.property.service.dto.ComparableDTO;
import com.geointelli.ai.property.service.dto.ComparableResponse;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.ComparableCandidateService;
import com.geointelli.ai.property.service.service.ComparableRankingService;

/** Verifies that the full pool is ranked before response limits are applied. */
class ComparableServiceImplTest {
    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 22);
    private final ComparableCandidateService candidates = mock(ComparableCandidateService.class);
    private final ComparableRankingService ranking = mock(ComparableRankingService.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final ComparableServiceImpl service = new ComparableServiceImpl(candidates, ranking, properties);

    @Test
    void ranksTheEntirePoolBeforeLimitingAndPreservesTheRequestedFilters() {
        ComparableCandidateResponse pool = preparePool(100);
        pool.setSaleDateFrom(AS_OF.minusMonths(12));
        ComparableCandidateRequest request = new ComparableCandidateRequest();
        request.setMonths(12);
        request.setRadiusMeters(3500);
        request.setAreaTolerancePercent(10);
        request.setBedroomTolerance(0);
        request.setLimit(3);

        ComparableResponse result = service.findComparables(1L, request);

        ArgumentCaptor<ComparableCandidateRequest> captured = ArgumentCaptor.forClass(ComparableCandidateRequest.class);
        verify(candidates).findCandidates(eq(1L), captured.capture());
        assertEquals(100, captured.getValue().getLimit());
        assertEquals(12, captured.getValue().getMonths());
        assertEquals(3500, captured.getValue().getRadiusMeters());
        assertEquals(10, captured.getValue().getAreaTolerancePercent());
        assertEquals(0, captured.getValue().getBedroomTolerance());
        assertEquals(3, request.getLimit());
        assertNotSame(request, captured.getValue());
        verify(ranking).rank(any(Property.class), same(pool.getCandidates()), eq(AS_OF), eq(3500));
        assertEquals(List.of(1099L, 1098L, 1097L), result.getComparables().stream()
            .map(item -> item.getCandidate().getPropertyId()).toList());
        assertEquals(3, result.getReturnedCount());
        assertEquals(100, result.getConsideredCandidates());
        assertTrue(result.isCandidatePoolLimitReached());
        assertEquals("COMPARABLES_FOUND", result.getStatus());
        assertEquals("qualified-sales-v1", result.getSelectionVersion());
        assertEquals("comparables-score-v1", result.getRankingVersion());
        assertEquals(Map.of("location", 20), result.getScoringWeights());
        assertEquals(AS_OF, result.getAsOfDate());
        assertEquals(AS_OF.minusMonths(12), result.getSaleDateFrom());
        assertEquals(31L, result.getSubject().countyId());
        assertEquals("0101", result.getSubject().dorCode());
        assertEquals(new BigDecimal("2000"), result.getSubject().livingAreaSqft());
        assertTrue(result.getWarnings().stream().anyMatch(message -> message.contains("pool limit")));
        assertEquals(List.of("Source freshness unavailable"), pool.getWarnings());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void returnsPartialOrEmptyResultsWithoutInventingComparables(int count) {
        preparePool(count);
        ComparableResponse result = service.findComparables(1L, new ComparableCandidateRequest());
        assertEquals("INSUFFICIENT_DATA", result.getStatus());
        assertEquals(count, result.getReturnedCount());
        assertEquals(count, result.getComparables().size());
        assertFalse(result.isCandidatePoolLimitReached());
        assertTrue(result.getWarnings().contains("Source freshness unavailable"));
    }

    @Test
    void keepsTheSameTopResultsWhenOnlyTheResponseLimitChanges() {
        preparePool(8);
        ComparableCandidateRequest small = new ComparableCandidateRequest();
        small.setLimit(3);
        ComparableCandidateRequest large = new ComparableCandidateRequest();
        large.setLimit(5);
        ComparableResponse first = service.findComparables(1L, small);
        ComparableResponse second = service.findComparables(1L, large);
        assertEquals(second.getComparables().subList(0, 3), first.getComparables());
        assertEquals(8, first.getConsideredCandidates());
        assertEquals(8, second.getConsideredCandidates());
    }

    @Test
    void propagatesUnknownPropertyErrorsWithoutCallingRanking() {
        when(candidates.findCandidates(eq(99L), any())).thenThrow(new PropertyNotFoundException("Unknown property"));
        assertThrows(PropertyNotFoundException.class,
            () -> service.findComparables(99L, new ComparableCandidateRequest()));
        verifyNoInteractions(properties, ranking);
    }

    @Test
    void rejectsAPropertyThatDisappearsBeforeReadingSubjectFacts() {
        when(candidates.findCandidates(eq(1L), any())).thenReturn(pool(0));
        when(properties.findById(1L)).thenReturn(Optional.empty());
        assertThrows(PropertyNotFoundException.class,
            () -> service.findComparables(1L, new ComparableCandidateRequest()));
        verifyNoInteractions(ranking);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 101})
    void rejectsInvalidResponseLimitsBeforeAccessingDependencies(int limit) {
        ComparableCandidateRequest request = new ComparableCandidateRequest();
        request.setLimit(limit);
        assertThrows(IllegalArgumentException.class, () -> service.findComparables(1L, request));
        verifyNoInteractions(candidates, ranking, properties);
    }

    @Test
    void rejectsNullRequestsBeforeAccessingDependencies() {
        assertThrows(IllegalArgumentException.class, () -> service.findComparables(1L, null));
        verifyNoInteractions(candidates, ranking, properties);
    }

    private ComparableCandidateResponse preparePool(int count) {
        ComparableCandidateResponse pool = pool(count);
        County county = new County();
        county.setId(31L);
        Property subject = new Property();
        subject.setId(1L);
        subject.setCounty(county);
        subject.setDorCode("0101");
        subject.setBuildingHeatedArea(new BigDecimal("2000"));
        when(candidates.findCandidates(eq(1L), any())).thenReturn(pool);
        when(properties.findById(1L)).thenReturn(Optional.of(subject));
        List<ComparableDTO> ranked = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            ComparableDTO item = new ComparableDTO();
            item.setCandidate(pool.getCandidates().get(i));
            item.setRank(count - i);
            item.setSimilarityScore(BigDecimal.valueOf(i));
            ranked.add(item);
        }
        when(ranking.rank(same(subject), same(pool.getCandidates()), eq(AS_OF), anyInt())).thenReturn(ranked);
        when(ranking.getVersion()).thenReturn("comparables-score-v1");
        when(ranking.getWeights()).thenReturn(Map.of("location", 20));
        return pool;
    }

    private ComparableCandidateResponse pool(int count) {
        List<ComparableCandidateDTO> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(ComparableCandidateDTO.builder().propertyId(1000L + i).build());
        }
        return ComparableCandidateResponse.builder().propertyId(1L).asOfDate(AS_OF)
            .saleDateFrom(AS_OF.minusMonths(24)).selectionVersion("qualified-sales-v1")
            .minimumCandidates(3).returnedCount(count).candidates(rows)
            .warnings(List.of("Source freshness unavailable")).build();
    }
}
