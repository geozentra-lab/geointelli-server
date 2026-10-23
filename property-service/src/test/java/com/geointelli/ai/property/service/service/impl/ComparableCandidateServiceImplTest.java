package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.geointelli.ai.property.service.dto.ComparableCandidateDTO;
import com.geointelli.ai.property.service.dto.ComparableCandidateRequest;
import com.geointelli.ai.property.service.dto.ComparableCandidateResponse;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.ComparableCandidateProjection;
import com.geointelli.ai.property.service.repository.ComparableCandidateRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;

/** Verifies retrieval orchestration and missing-data behavior using mocked repositories. */
class ComparableCandidateServiceImplTest {
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final ComparableCandidateRepository candidates = mock(ComparableCandidateRepository.class);
    private final ComparableCandidateServiceImpl service = new ComparableCandidateServiceImpl(properties, candidates);

    @Test
    void passesTheRequestedFiltersAndMapsRecordedSaleEvidence() {
        Property subject = subject();
        when(properties.findById(1L)).thenReturn(Optional.of(subject));
        when(candidates.hasGeometry(1L, null)).thenReturn(true);
        ComparableCandidateProjection row = mock(ComparableCandidateProjection.class);
        when(row.getPropertyId()).thenReturn(2L);
        when(row.getCountyId()).thenReturn(31L);
        when(row.getFolio()).thenReturn("parcel-2");
        when(row.getAddress()).thenReturn("123 Main Street");
        when(row.getDorCode()).thenReturn("0101");
        when(row.getBedrooms()).thenReturn(new BigDecimal("3"));
        when(row.getBathrooms()).thenReturn(new BigDecimal("2"));
        when(row.getLivingAreaSqft()).thenReturn(new BigDecimal("1800"));
        when(row.getSaleId()).thenReturn(20L);
        when(row.getSaleDate()).thenReturn(LocalDate.of(2025, 10, 1));
        when(row.getSalePrice()).thenReturn(new BigDecimal("690000.00"));
        when(row.getQualifiedFlag()).thenReturn("Q");
        when(row.getQualificationDescription()).thenReturn("Qual by exam of deed");
        when(row.getInstrumentNumber()).thenReturn("deed-20");
        when(row.getMatchLevel()).thenReturn("SAME_SUBDIVISION");
        when(row.getDistanceMeters()).thenReturn(72.0);
        ComparableCandidateProjection second = mock(ComparableCandidateProjection.class);
        ComparableCandidateProjection third = mock(ComparableCandidateProjection.class);
        when(second.getPropertyId()).thenReturn(3L);
        when(third.getPropertyId()).thenReturn(4L);
        when(candidates.findCandidates(eq(1L), any(), any(), eq(3000), eq(10), eq(0), eq(5)))
            .thenReturn(List.of(row, second, third));

        ComparableCandidateRequest request = new ComparableCandidateRequest();
        request.setMonths(12);
        request.setRadiusMeters(3000);
        request.setAreaTolerancePercent(10);
        request.setBedroomTolerance(0);
        request.setLimit(5);
        LocalDate before = LocalDate.now(ZoneOffset.UTC);
        ComparableCandidateResponse result = service.findCandidates(1L, request);
        LocalDate after = LocalDate.now(ZoneOffset.UTC);

        assertFalse(result.getAsOfDate().isBefore(before));
        assertFalse(result.getAsOfDate().isAfter(after));
        assertEquals(result.getAsOfDate().minusMonths(12), result.getSaleDateFrom());
        verify(candidates).findCandidates(1L, result.getSaleDateFrom(), result.getAsOfDate(), 3000, 10, 0, 5);
        assertEquals("CANDIDATES_FOUND", result.getStatus());
        assertEquals(3, result.getReturnedCount());
        assertEquals(3, result.getMinimumCandidates());
        ComparableCandidateDTO mapped = result.getCandidates().get(0);
        assertEquals(2L, mapped.getPropertyId());
        assertEquals(31L, mapped.getCountyId());
        assertEquals("parcel-2", mapped.getFolio());
        assertEquals("123 Main Street", mapped.getAddress());
        assertEquals("0101", mapped.getDorCode());
        assertEquals(new BigDecimal("3"), mapped.getBedrooms());
        assertEquals(new BigDecimal("2"), mapped.getBathrooms());
        assertEquals(new BigDecimal("1800"), mapped.getLivingAreaSqft());
        assertEquals(20L, mapped.getSaleId());
        assertEquals(LocalDate.of(2025, 10, 1), mapped.getSaleDate());
        assertEquals(new BigDecimal("690000.00"), mapped.getSalePrice());
        assertEquals("Q", mapped.getQualifiedFlag());
        assertEquals("Qual by exam of deed", mapped.getQualificationDescription());
        assertEquals("deed-20", mapped.getInstrumentNumber());
        assertEquals("SAME_SUBDIVISION", mapped.getMatchLevel());
        assertEquals(72.0, mapped.getDistanceMeters());
    }

    @Test
    void permitsNullFolioChildrenAndChecksTheirParentGeometry() {
        Property child = subject();
        child.setFolio(null);
        Property parent = new Property();
        parent.setId(9L);
        child.setParentProperty(parent);
        when(properties.findById(1L)).thenReturn(Optional.of(child));
        when(candidates.hasGeometry(1L, 9L)).thenReturn(true);
        when(candidates.findCandidates(eq(1L), any(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
            .thenReturn(List.of());
        ComparableCandidateResponse result = service.findCandidates(1L, new ComparableCandidateRequest());
        verify(candidates).hasGeometry(1L, 9L);
        assertEquals("INSUFFICIENT_DATA", result.getStatus());
        assertTrue(result.getCandidates().isEmpty());
        assertFalse(result.getWarnings().stream().anyMatch(message -> message.contains("no usable parcel")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"county", "type"})
    void avoidsUnsafeSearchesWhenTheSubjectHasNoCountyOrType(String missingField) {
        Property subject = subject();
        if ("county".equals(missingField)) {
            subject.setCounty(null);
        } else {
            subject.setDorCode(" ");
        }
        when(properties.findById(1L)).thenReturn(Optional.of(subject));
        ComparableCandidateResponse result = service.findCandidates(1L, new ComparableCandidateRequest());
        assertEquals("INSUFFICIENT_DATA", result.getStatus());
        assertEquals(0, result.getReturnedCount());
        assertTrue(result.getWarnings().stream().anyMatch(message -> message.contains("cannot be safely matched")));
        verifyNoInteractions(candidates);
    }

    @Test
    void disclosesMissingGeometryAndSimilarityFactsWithoutDiscardingAvailableRows() {
        Property subject = subject();
        subject.setBuildingHeatedArea(null);
        subject.setBedroomCount(null);
        when(properties.findById(1L)).thenReturn(Optional.of(subject));
        ComparableCandidateProjection row = mock(ComparableCandidateProjection.class);
        when(candidates.findCandidates(eq(1L), any(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
            .thenReturn(List.of(row));
        ComparableCandidateResponse result = service.findCandidates(1L, new ComparableCandidateRequest());
        assertEquals(1, result.getReturnedCount());
        assertEquals("INSUFFICIENT_DATA", result.getStatus());
        assertTrue(result.getWarnings().stream().anyMatch(message -> message.contains("no usable parcel")));
        assertTrue(result.getWarnings().stream().anyMatch(message -> message.contains("no positive heated area")));
        assertTrue(result.getWarnings().stream().anyMatch(message -> message.contains("no bedroom count")));
    }

    @Test
    void rejectsUnknownPropertiesBeforeQueryingCandidates() {
        when(properties.findById(99L)).thenReturn(Optional.empty());
        assertThrows(PropertyNotFoundException.class,
            () -> service.findCandidates(99L, new ComparableCandidateRequest()));
        verifyNoInteractions(candidates);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidPropertyIdsBeforeAccessingRepositories(Long id) {
        assertThrows(IllegalArgumentException.class,
            () -> service.findCandidates(id, new ComparableCandidateRequest()));
        verifyNoInteractions(properties, candidates);
    }

    @ParameterizedTest
    @CsvSource({"months,0", "months,121", "radius,99", "radius,10001",
        "area,-1", "area,101", "bedrooms,-1", "bedrooms,6", "limit,2", "limit,101"})
    void rejectsInvalidSearchLimitsBeforeAccessingRepositories(String field, int value) {
        ComparableCandidateRequest request = new ComparableCandidateRequest();
        switch (field) {
            case "months": request.setMonths(value); break;
            case "radius": request.setRadiusMeters(value); break;
            case "area": request.setAreaTolerancePercent(value); break;
            case "bedrooms": request.setBedroomTolerance(value); break;
            case "limit": request.setLimit(value); break;
            default: throw new IllegalArgumentException(field);
        }
        assertThrows(IllegalArgumentException.class, () -> service.findCandidates(1L, request));
        verifyNoInteractions(properties, candidates);
    }

    @Test
    void rejectsNullRequestsBeforeAccessingRepositories() {
        assertThrows(IllegalArgumentException.class, () -> service.findCandidates(1L, null));
        verifyNoInteractions(properties, candidates);
    }

    private Property subject() {
        County county = new County();
        county.setId(31L);
        Property property = new Property();
        property.setId(1L);
        property.setCounty(county);
        property.setDorCode("0101");
        property.setBuildingHeatedArea(new BigDecimal("2000"));
        property.setBedroomCount(new BigDecimal("4"));
        return property;
    }
}
