package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.geointelli.ai.property.service.dto.ComparableCandidateDTO;
import com.geointelli.ai.property.service.dto.ComparableDTO;
import com.geointelli.ai.property.service.entity.Property;

/** Verifies ranking rules, missing evidence and deterministic tie-breaking without a database. */
class ComparableRankingServiceImplTest {
    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 22);
    private final ComparableRankingServiceImpl service = new ComparableRankingServiceImpl();

    @Test
    void givesAnExactSameParentMatchOneHundredPoints() {
        ComparableDTO result = rank(candidate(2L));
        assertEquals(new BigDecimal("100.00"), result.getSimilarityScore());
        assertEquals(100, result.getEvidenceCoveragePercent());
        assertEquals(1, result.getRank());
        assertTrue(result.getMissingData().isEmpty());
        assertTrue(result.getSelectionReasons().stream().anyMatch(reason -> reason.contains("not verify a shared building")));
        assertEquals(100, service.getWeights().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals("comparables-score-v1", service.getVersion());
    }

    @ParameterizedTest
    @CsvSource({"SAME_PARENT_GROUP,20.00", "SAME_SUBDIVISION,18.00",
        "SAME_NEIGHBORHOOD,12.00", "NEARBY,6.00"})
    void awardsTheDocumentedLocationPoints(String matchLevel, String points) {
        ComparableCandidateDTO candidate = candidate(2L);
        candidate.setMatchLevel(matchLevel);
        assertEquals(new BigDecimal(points), rank(candidate).getScoreBreakdown().getLocation());
    }

    @Test
    void scoresFractionalBathroomsAndAreaDifferencesWithRoundedTotals() {
        Property subject = subject();
        subject.setBathroomCount(new BigDecimal("2.5"));
        ComparableCandidateDTO candidate = candidate(2L);
        candidate.setLivingAreaSqft(new BigDecimal("1800"));
        candidate.setBedrooms(new BigDecimal("3"));
        ComparableDTO result = service.rank(subject, List.of(candidate), AS_OF, 2000).get(0);
        assertEquals(new BigDecimal("16.00"), result.getScoreBreakdown().getLivingArea());
        assertEquals(new BigDecimal("6.67"), result.getScoreBreakdown().getBedrooms());
        assertEquals(new BigDecimal("7.50"), result.getScoreBreakdown().getBathrooms());
        assertEquals(new BigDecimal("90.17"), result.getSimilarityScore());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Multiple (See Building Info.)", "199x", "0000", "0999", "2027", "19940"})
    void disclosesUnavailableBuildYearsWithoutNormalizingAwayLostPoints(String year) {
        ComparableCandidateDTO candidate = candidate(2L);
        candidate.setYearBuilt(year);
        ComparableDTO result = rank(candidate);
        assertNull(result.getScoreBreakdown().getYearBuilt());
        assertEquals(90, result.getEvidenceCoveragePercent());
        assertEquals(new BigDecimal("90.00"), result.getSimilarityScore());
        assertTrue(result.getMissingData().stream().anyMatch(message -> message.contains("build year")));
    }

    @Test
    void treatsMissingSubjectFactsAsUnavailableEvenWhenTheCandidateHasThem() {
        Property subject = subject();
        subject.setBuildingHeatedArea(BigDecimal.ZERO);
        subject.setBedroomCount(null);
        subject.setBathroomCount(null);
        subject.setYearBuilt(null);
        ComparableDTO result = service.rank(subject, List.of(candidate(2L)), AS_OF, 2000).get(0);
        assertNull(result.getScoreBreakdown().getLivingArea());
        assertNull(result.getScoreBreakdown().getBedrooms());
        assertNull(result.getScoreBreakdown().getBathrooms());
        assertNull(result.getScoreBreakdown().getYearBuilt());
        assertEquals(50, result.getEvidenceCoveragePercent());
        assertEquals(new BigDecimal("50.00"), result.getSimilarityScore());
    }

    @Test
    void distinguishesKnownPoorMatchesFromMissingEvidenceAndNeverAwardsNegativePoints() {
        ComparableCandidateDTO candidate = candidate(2L);
        candidate.setMatchLevel("NEARBY");
        candidate.setDistanceMeters(4000.0);
        candidate.setLivingAreaSqft(new BigDecimal("4000"));
        candidate.setBedrooms(new BigDecimal("7"));
        candidate.setBathrooms(new BigDecimal("4"));
        candidate.setYearBuilt("1954");
        candidate.setSaleDate(AS_OF.minusYears(4));
        ComparableDTO result = rank(candidate);
        assertEquals(new BigDecimal("6.00"), result.getSimilarityScore());
        assertEquals(100, result.getEvidenceCoveragePercent());
        assertTrue(result.getMissingData().isEmpty());
        assertEquals(new BigDecimal("0.00"), result.getScoreBreakdown().getDistance());
        assertEquals(new BigDecimal("0.00"), result.getScoreBreakdown().getYearBuilt());
        assertEquals(new BigDecimal("0.00"), result.getScoreBreakdown().getSaleRecency());
    }

    @Test
    void handlesInvalidEvidenceWithoutFabricatingScores() {
        ComparableCandidateDTO candidate = candidate(2L);
        candidate.setMatchLevel(null);
        candidate.setDistanceMeters(Double.NaN);
        candidate.setLivingAreaSqft(BigDecimal.ZERO);
        candidate.setBedrooms(new BigDecimal("-1"));
        candidate.setBathrooms(null);
        candidate.setYearBuilt("Multiple");
        candidate.setSaleDate(AS_OF.plusDays(1));
        ComparableDTO result = rank(candidate);
        assertEquals(new BigDecimal("0.00"), result.getSimilarityScore());
        assertEquals(0, result.getEvidenceCoveragePercent());
        assertEquals(7, result.getMissingData().size());
    }

    @Test
    void ranksSimilarityBeforeInputOrderOrSalePrice() {
        ComparableCandidateDTO weak = candidate(1L);
        weak.setMatchLevel("NEARBY");
        weak.setLivingAreaSqft(new BigDecimal("2500"));
        weak.setSalePrice(new BigDecimal("10000000"));
        ComparableCandidateDTO strong = candidate(2L);
        strong.setSalePrice(new BigDecimal("100000"));
        List<ComparableDTO> result = service.rank(subject(), List.of(weak, strong), AS_OF, 2000);
        assertEquals(2L, result.get(0).getCandidate().getPropertyId());
        assertEquals(1, result.get(0).getRank());
        assertEquals(2, result.get(1).getRank());
    }

    @Test
    void prefersMoreCompleteEvidenceWhenScoresTie() {
        ComparableCandidateDTO missingYear = candidate(1L);
        missingYear.setYearBuilt(null);
        ComparableCandidateDTO knownOldYear = candidate(2L);
        knownOldYear.setYearBuilt("1954");
        List<ComparableDTO> result = service.rank(subject(), List.of(missingYear, knownOldYear), AS_OF, 2000);
        assertEquals(result.get(0).getSimilarityScore(), result.get(1).getSimilarityScore());
        assertEquals(2L, result.get(0).getCandidate().getPropertyId());
    }

    @Test
    void usesSaleDateThenDistanceThenStableIdsForEqualScores() {
        ComparableCandidateDTO older = candidate(1L);
        older.setSaleDate(AS_OF.minusYears(5));
        ComparableCandidateDTO newer = candidate(2L);
        newer.setSaleDate(AS_OF.minusYears(4));
        assertEquals(2L, service.rank(subject(), List.of(older, newer), AS_OF, 2000)
            .get(0).getCandidate().getPropertyId());

        ComparableCandidateDTO farther = candidate(1L);
        farther.setDistanceMeters(6000.0);
        ComparableCandidateDTO nearer = candidate(2L);
        nearer.setDistanceMeters(4000.0);
        assertEquals(2L, service.rank(subject(), List.of(farther, nearer), AS_OF, 2000)
            .get(0).getCandidate().getPropertyId());

        List<ComparableCandidateDTO> unordered = List.of(candidate(3L), candidate(1L), candidate(2L));
        List<Long> expected = List.of(1L, 2L, 3L);
        assertEquals(expected, service.rank(subject(), unordered, AS_OF, 2000).stream()
            .map(item -> item.getCandidate().getPropertyId()).toList());
        assertEquals(expected, service.rank(subject(), unordered, AS_OF, 2000).stream()
            .map(item -> item.getCandidate().getPropertyId()).toList());
    }

    @Test
    void returnsEmptyResultsAndRejectsInvalidRankingContext() {
        assertTrue(service.rank(subject(), List.of(), AS_OF, 2000).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.rank(null, List.of(), AS_OF, 2000));
        assertThrows(IllegalArgumentException.class, () -> service.rank(subject(), null, AS_OF, 2000));
        assertThrows(IllegalArgumentException.class, () -> service.rank(subject(), List.of(), null, 2000));
        assertThrows(IllegalArgumentException.class, () -> service.rank(subject(), List.of(), AS_OF, 0));
    }

    private ComparableDTO rank(ComparableCandidateDTO candidate) {
        return service.rank(subject(), List.of(candidate), AS_OF, 2000).get(0);
    }

    private Property subject() {
        Property subject = new Property();
        subject.setBuildingHeatedArea(new BigDecimal("2000"));
        subject.setBedroomCount(new BigDecimal("4"));
        subject.setBathroomCount(new BigDecimal("2"));
        subject.setYearBuilt("1994");
        return subject;
    }

    private ComparableCandidateDTO candidate(long id) {
        return ComparableCandidateDTO.builder().propertyId(id).saleId(id + 100)
            .matchLevel("SAME_PARENT_GROUP").distanceMeters(0.0)
            .livingAreaSqft(new BigDecimal("2000")).bedrooms(new BigDecimal("4"))
            .bathrooms(new BigDecimal("2")).yearBuilt("1994").saleDate(AS_OF).build();
    }
}
