package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.AddressDTO;
import com.geointelli.ai.property.service.dto.AssessmentDTO;
import com.geointelli.ai.property.service.dto.PropertyCompareRequest;
import com.geointelli.ai.property.service.dto.PropertyCompareResponse;
import com.geointelli.ai.property.service.dto.PropertyComparisonDTO;
import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.dto.PropertyValuePredictionDTO;
import com.geointelli.ai.property.service.dto.SaleDTO;
import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.dto.TransportationAnalysisDTO;
import com.geointelli.ai.property.service.exception.PropertyValuePredictionNotFoundException;
import com.geointelli.ai.property.service.service.PropertyCompareService;
import com.geointelli.ai.property.service.service.PropertyService;
import com.geointelli.ai.property.service.service.PropertyValuePredictionService;
import com.geointelli.ai.property.service.service.SchoolAnalysisService;
import com.geointelli.ai.property.service.service.TransportationAnalysisService;

import lombok.RequiredArgsConstructor;

/** Assembles comparison columns from the existing property, valuation and analysis services. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyCompareServiceImpl implements PropertyCompareService {
    /** Units of every measured field; the same for all columns. */
    private static final Map<String, String> UNITS = Map.of(
        "currency", "USD",
        "area", "sqft",
        "lotSize", "sqft");

    private final PropertyService propertyService;
    private final PropertyValuePredictionService predictionService;
    private final SchoolAnalysisService schoolAnalysisService;
    private final TransportationAnalysisService transportationAnalysisService;

    /** {@inheritDoc} */
    @Override
    public PropertyCompareResponse compare(PropertyCompareRequest request) {
        List<Long> ids = request.getPropertyIds();
        rejectDuplicates(ids);

        // Facts: one call per ID, in request order; an unknown ID throws PropertyNotFoundException (404).
        List<PropertyDTO> properties = ids.stream().map(propertyService::getPropertyById).toList();

        // Valuation and scores: one bulk call each, then indexed by property ID.
        Map<Long, PropertyValuePredictionDTO> valuations = loadValuations(ids);
        Map<Long, SchoolAnalysisDTO> schools = indexById(
            schoolAnalysisService.getPropertySchoolAnalysisByIds(ids), SchoolAnalysisDTO::getPropertyId);
        Map<Long, TransportationAnalysisDTO> transports = indexById(
            transportationAnalysisService.getPropertyTransportationAnalysisByIds(ids),
            TransportationAnalysisDTO::getPropertyId);

        Integer commonYear = commonAssessmentYear(properties);
        List<String> warnings = new ArrayList<>();
        if (commonYear == null) {
            warnings.add("No assessment year is shared by all properties; each column shows its own latest year.");
        }

        List<PropertyComparisonDTO> columns = properties.stream()
            .map(p -> toColumn(p, commonYear, valuations.get(p.getId()), schools.get(p.getId()), transports.get(p.getId())))
            .toList();

        PropertyCompareResponse response = new PropertyCompareResponse();
        response.setRequestedIds(List.copyOf(ids));
        response.setComparedCount(columns.size());
        response.setUnits(UNITS);
        response.setAssessmentYear(commonYear);
        response.setWarnings(warnings);
        response.setProperties(columns);
        return response;
    }

    private static void rejectDuplicates(List<Long> ids) {
        Set<Long> seen = new HashSet<>();
        List<Long> duplicates = ids.stream().filter(id -> !seen.add(id)).distinct().toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("propertyIds contains duplicates: " + duplicates);
        }
    }

    /** The prediction service throws when no property has a valuation; here that simply means none available. */
    private Map<Long, PropertyValuePredictionDTO> loadValuations(List<Long> ids) {
        try {
            return indexById(predictionService.getPropertyValuePredictionByIds(ids), PropertyValuePredictionDTO::getPropertyId);
        } catch (PropertyValuePredictionNotFoundException e) {
            return Map.of();
        }
    }

    private static <T> Map<Long, T> indexById(List<T> items, Function<T, Long> idOf) {
        return items.stream().collect(Collectors.toMap(idOf, Function.identity(), (first, second) -> first));
    }

    /**
     * Latest assessment year present on every property that has assessments at all, or
     * {@code null} when the properties share no year.
     */
    private static Integer commonAssessmentYear(List<PropertyDTO> properties) {
        Set<Integer> common = null;
        for (PropertyDTO property : properties) {
            Set<Integer> years = assessments(property).map(AssessmentDTO::getYear)
                .filter(Objects::nonNull).collect(Collectors.toSet());
            if (years.isEmpty()) continue;
            if (common == null) common = new HashSet<>(years); else common.retainAll(years);
        }
        return common == null ? null : common.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private static PropertyComparisonDTO toColumn(PropertyDTO p, Integer commonYear,
            PropertyValuePredictionDTO valuation, SchoolAnalysisDTO school, TransportationAnalysisDTO transport) {
        List<String> missing = new ArrayList<>();
        PropertyComparisonDTO column = new PropertyComparisonDTO();
        column.setPropertyId(p.getId());
        column.setFolio(p.getFolio());
        column.setAddress(formatAddress(p.getAddress()));
        column.setCountyName(p.getCountyName());
        column.setStateCode(p.getStateCode());
        track(missing, "address", column.getAddress());

        column.setFacts(new PropertyComparisonDTO.Facts(
            p.getBedroomCount(), p.getBathroomCount(), p.getBuildingHeatedArea(), p.getLotSize(),
            p.getYearBuilt(), propertyType(p), p.getFloorCount(), p.getUnitCount()));
        track(missing, "facts.bedrooms", p.getBedroomCount());
        track(missing, "facts.bathrooms", p.getBathroomCount());
        track(missing, "facts.livingAreaSqft", p.getBuildingHeatedArea());
        track(missing, "facts.lotSizeSqft", p.getLotSize());
        track(missing, "facts.yearBuilt", p.getYearBuilt());
        track(missing, "facts.propertyType", propertyType(p));
        track(missing, "facts.floors", p.getFloorCount());
        track(missing, "facts.units", p.getUnitCount());

        AssessmentDTO assessment = selectAssessment(p, commonYear);
        if (assessment == null) {
            missing.add("assessment");
        } else {
            column.setAssessment(new PropertyComparisonDTO.Assessment(assessment.getYear(), assessment.getTotalValue(),
                assessment.getLandValue(), assessment.getBuildingOnlyValue(), assessment.getAssessedValue()));
            track(missing, "assessment.totalValue", assessment.getTotalValue());
            track(missing, "assessment.landValue", assessment.getLandValue());
            track(missing, "assessment.buildingValue", assessment.getBuildingOnlyValue());
            track(missing, "assessment.assessedValue", assessment.getAssessedValue());
        }

        SaleDTO lastSale = sales(p).filter(s -> s.getSaleDate() != null)
            .max(Comparator.comparing(SaleDTO::getSaleDate)).orElse(null);
        if (lastSale == null) {
            missing.add("lastSale");
        } else {
            column.setLastSale(new PropertyComparisonDTO.LastSale(lastSale.getSaleDate(), lastSale.getSalePrice()));
            track(missing, "lastSale.price", lastSale.getSalePrice());
        }

        if (valuation == null) {
            missing.add("valuation");
        } else {
            column.setValuation(new PropertyComparisonDTO.Valuation(valuation.getPredictedPrice(),
                valuation.getPredictedPriceLow(), valuation.getPredictedPriceHigh(), valuation.getGrowthPercentage(),
                valuation.getConfidenceScore(), valuation.getPredictionDate()));
        }

        Double schoolScore = school == null ? null : school.getSchoolScore();
        BigDecimal transportationScore = transport == null ? null : transport.getTransportationScore();
        column.setScores(new PropertyComparisonDTO.Scores(schoolScore, transportationScore));
        track(missing, "scores.schoolScore", schoolScore);
        track(missing, "scores.transportationScore", transportationScore);

        column.setMissingFields(missing);
        return column;
    }

    /** The assessment of the common year, or the latest one of the property when there is no common year. */
    private static AssessmentDTO selectAssessment(PropertyDTO p, Integer commonYear) {
        if (commonYear != null) {
            return assessments(p).filter(a -> commonYear.equals(a.getYear())).findFirst().orElse(null);
        }
        return assessments(p).filter(a -> a.getYear() != null)
            .max(Comparator.comparing(AssessmentDTO::getYear)).orElse(null);
    }

    private static Stream<AssessmentDTO> assessments(PropertyDTO p) {
        return p.getAssessments() == null ? Stream.empty() : p.getAssessments().stream();
    }

    private static Stream<SaleDTO> sales(PropertyDTO p) {
        return p.getSales() == null ? Stream.empty() : p.getSales().stream();
    }

    private static String propertyType(PropertyDTO p) {
        return joinNonBlank(" ", p.getDorCode(), p.getDorDescription());
    }

    /** The stored address line already includes city, state and zip; fall back to city and zip without it. */
    private static String formatAddress(AddressDTO a) {
        if (a == null) return null;
        if (a.getAddress() != null && !a.getAddress().isBlank()) return a.getAddress().trim();
        return joinNonBlank(" ", a.getCity(), a.getZip());
    }

    private static String joinNonBlank(String separator, String... parts) {
        String joined = Stream.of(parts).filter(s -> s != null && !s.isBlank()).map(String::trim)
            .collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }

    private static void track(List<String> missing, String path, Object value) {
        if (value == null) missing.add(path);
    }
}
