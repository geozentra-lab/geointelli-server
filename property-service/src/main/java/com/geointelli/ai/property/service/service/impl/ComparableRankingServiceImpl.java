package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.dto.ComparableCandidateDTO;
import com.geointelli.ai.property.service.dto.ComparableDTO;
import com.geointelli.ai.property.service.dto.ComparableScoreBreakdownDTO;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.service.ComparableRankingService;

/** Applies versioned similarity rules; no model or price adjustment is involved. */
@Service
public class ComparableRankingServiceImpl implements ComparableRankingService {
    private static final Map<String, Integer> WEIGHTS = Map.of(
        "location", 20, "distance", 15, "livingArea", 20,
        "bedrooms", 10, "bathrooms", 10, "yearBuilt", 10, "saleRecency", 15);

    @Override
    public String getVersion() {
        return "comparables-score-v1";
    }

    @Override
    public Map<String, Integer> getWeights() {
        return WEIGHTS;
    }

    @Override
    public List<ComparableDTO> rank(Property subject, List<ComparableCandidateDTO> candidates,
            LocalDate asOfDate, int radiusMeters) {
        if (subject == null || candidates == null || asOfDate == null || radiusMeters <= 0) {
            throw new IllegalArgumentException("Subject, candidates, asOfDate and positive radius are required");
        }
        List<ComparableDTO> ranked = new ArrayList<>();
        for (ComparableCandidateDTO candidate : candidates) {
            ranked.add(score(subject, candidate, asOfDate, radiusMeters));
        }
        ranked.sort(Comparator.comparing(ComparableDTO::getSimilarityScore).reversed()
            .thenComparing(Comparator.comparingInt(ComparableDTO::getEvidenceCoveragePercent).reversed())
            .thenComparing(item -> item.getCandidate().getSaleDate(),
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(item -> item.getCandidate().getDistanceMeters(),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(item -> item.getCandidate().getPropertyId(),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(item -> item.getCandidate().getSaleId(),
                Comparator.nullsLast(Comparator.naturalOrder())));
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).setRank(i + 1);
        }
        return ranked;
    }

    private ComparableDTO score(Property subject, ComparableCandidateDTO candidate,
            LocalDate asOfDate, int radiusMeters) {
        List<String> reasons = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        ComparableScoreBreakdownDTO breakdown = new ComparableScoreBreakdownDTO();
        breakdown.setLocation(location(candidate.getMatchLevel(), reasons, missing));
        breakdown.setDistance(distance(candidate.getDistanceMeters(), radiusMeters, reasons, missing));
        breakdown.setLivingArea(area(subject.getBuildingHeatedArea(), candidate.getLivingAreaSqft(), reasons, missing));
        breakdown.setBedrooms(rooms("Bedrooms", subject.getBedroomCount(), candidate.getBedrooms(),
            3, reasons, missing));
        breakdown.setBathrooms(rooms("Bathrooms", subject.getBathroomCount(), candidate.getBathrooms(),
            2, reasons, missing));
        breakdown.setYearBuilt(buildYear(subject.getYearBuilt(), candidate.getYearBuilt(), asOfDate, reasons, missing));
        breakdown.setSaleRecency(recency(candidate.getSaleDate(), asOfDate, reasons, missing));

        BigDecimal[] awarded = {breakdown.getLocation(), breakdown.getDistance(), breakdown.getLivingArea(),
            breakdown.getBedrooms(), breakdown.getBathrooms(), breakdown.getYearBuilt(), breakdown.getSaleRecency()};
        int[] possible = {20, 15, 20, 10, 10, 10, 15};
        BigDecimal total = BigDecimal.ZERO;
        int coverage = 0;
        for (int i = 0; i < awarded.length; i++) {
            if (awarded[i] != null) {
                total = total.add(awarded[i]);
                coverage += possible[i];
            }
        }

        ComparableDTO result = new ComparableDTO();
        result.setCandidate(candidate);
        result.setSimilarityScore(total.setScale(2, RoundingMode.HALF_UP));
        result.setEvidenceCoveragePercent(coverage);
        result.setScoreBreakdown(breakdown);
        result.setSelectionReasons(reasons);
        result.setMissingData(missing);
        return result;
    }

    private BigDecimal location(String matchLevel, List<String> reasons, List<String> missing) {
        if ("SAME_PARENT_GROUP".equals(matchLevel)) {
            reasons.add("Same parent property group; this does not verify a shared building.");
            return points(1, 20);
        }
        if ("SAME_SUBDIVISION".equals(matchLevel)) {
            reasons.add("Same recorded subdivision.");
            return points(0.9, 20);
        }
        if ("SAME_NEIGHBORHOOD".equals(matchLevel)) {
            reasons.add("Same recorded neighborhood.");
            return points(0.6, 20);
        }
        if ("NEARBY".equals(matchLevel)) {
            reasons.add("Nearby property in the same county and DOR property type.");
            return points(0.3, 20);
        }
        missing.add("Location match classification is unavailable.");
        return null;
    }

    private BigDecimal distance(Double meters, int radiusMeters, List<String> reasons, List<String> missing) {
        if (meters == null || !Double.isFinite(meters) || meters < 0) {
            missing.add("Parcel distance is unavailable.");
            return null;
        }
        reasons.add("Parcel distance: " + number(meters) + " meters.");
        return points(1 - meters / radiusMeters, 15);
    }

    private BigDecimal area(BigDecimal subjectArea, BigDecimal candidateArea,
            List<String> reasons, List<String> missing) {
        if (!usable(subjectArea, true) || !usable(candidateArea, true)) {
            missing.add("Positive heated area is unavailable for the subject or candidate.");
            return null;
        }
        double difference = Math.abs(candidateArea.doubleValue() - subjectArea.doubleValue());
        double fraction = difference / subjectArea.doubleValue();
        reasons.add("Heated area difference: " + number(difference) + " sqft (" + number(fraction * 100) + "%).");
        return points(1 - fraction / 0.5, 20);
    }

    private BigDecimal rooms(String name, BigDecimal subjectRooms, BigDecimal candidateRooms,
            double zeroScoreDifference, List<String> reasons, List<String> missing) {
        if (!usable(subjectRooms, false) || !usable(candidateRooms, false)) {
            missing.add(name + " are unavailable for the subject or candidate.");
            return null;
        }
        double difference = Math.abs(candidateRooms.doubleValue() - subjectRooms.doubleValue());
        reasons.add(name + " difference: " + number(difference) + ".");
        return points(1 - difference / zeroScoreDifference, 10);
    }

    private BigDecimal buildYear(String subjectYear, String candidateYear, LocalDate asOfDate,
            List<String> reasons, List<String> missing) {
        Integer subject = parseYear(subjectYear, asOfDate.getYear());
        Integer candidate = parseYear(candidateYear, asOfDate.getYear());
        if (subject == null || candidate == null) {
            missing.add("A valid single build year is unavailable for the subject or candidate.");
            return null;
        }
        int difference = Math.abs(candidate - subject);
        reasons.add("Build-year difference: " + difference + " years.");
        return points(1 - difference / 40.0, 10);
    }

    private BigDecimal recency(LocalDate saleDate, LocalDate asOfDate,
            List<String> reasons, List<String> missing) {
        if (saleDate == null || saleDate.isAfter(asOfDate)) {
            missing.add("An eligible sale date is unavailable.");
            return null;
        }
        long days = ChronoUnit.DAYS.between(saleDate, asOfDate);
        long scale = ChronoUnit.DAYS.between(asOfDate.minusMonths(36), asOfDate);
        reasons.add("Recorded sale was " + days + " days before the as-of date.");
        return points(1 - (double) days / scale, 15);
    }

    private boolean usable(BigDecimal value, boolean positive) {
        return value != null && Double.isFinite(value.doubleValue())
            && (positive ? value.signum() > 0 : value.signum() >= 0);
    }

    private Integer parseYear(String value, int asOfYear) {
        if (value == null || !value.trim().matches("[0-9]{4}")) {
            return null;
        }
        int year = Integer.parseInt(value.trim());
        return year >= 1000 && year <= asOfYear ? year : null;
    }

    private BigDecimal points(double fraction, int maximum) {
        double bounded = Math.max(0, Math.min(1, fraction));
        return BigDecimal.valueOf(bounded * maximum).setScale(2, RoundingMode.HALF_UP);
    }

    private String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
