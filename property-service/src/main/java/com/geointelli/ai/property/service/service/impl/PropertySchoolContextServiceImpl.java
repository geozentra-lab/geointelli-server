package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.dto.SchoolContextDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.PropertySchoolContextService;
import com.geointelli.ai.property.service.service.SchoolAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the stored school analysis of the property through {@link SchoolAnalysisService} (nothing is
 * recalculated or saved) and labels it AVAILABLE, NO_NEARBY_RESULTS or NO_COVERAGE from the raw values,
 * never from the score. The stored score turns unknown values into 0, so it is only returned when every
 * value is known.
 */
@Service
@RequiredArgsConstructor
public class PropertySchoolContextServiceImpl implements PropertySchoolContextService {

    static final String NO_COVERAGE_NOTE = "No school data available for this property";
    static final String NO_NEARBY_NOTE = "No school within 1 km";
    static final String NO_NEARBY_PUBLIC_OR_CHARTER_NOTE = "No public or charter school within 1 km";
    static final double NEARBY_DISTANCE = 1000;

    private final PropertyRepository propertyRepository;
    private final SchoolAnalysisService schoolAnalysisService;

    @Override
    @Transactional(readOnly = true)
    public SchoolContextDTO getSchoolContext(Long propertyId) {
        // invalid ID -> 400, unknown property -> 404
        if (propertyId == null || propertyId <= 0) {
            throw new IllegalArgumentException("id must be a positive number");
        }
        // an analysis always belongs to an existing property, so only check the property when there is none
        SchoolAnalysisDTO analysis = schoolAnalysisService.getPropertySchoolAnalysis(propertyId).orElse(null);
        if (analysis == null && !propertyRepository.existsById(propertyId)) {
            throw new PropertyNotFoundException("No property found with id: " + propertyId);
        }

        SchoolContextDTO context = new SchoolContextDTO();
        context.setPropertyId(propertyId);
        context.setDistanceUnit(SchoolContextDTO.DISTANCE_UNIT);
        context.setSource(SchoolContextDTO.SOURCE);

        Double distPublic = analysis == null ? null : analysis.getDistPublicSchool();
        Double distCharter = analysis == null ? null : analysis.getDistCharterSchool();
        Integer within1km = analysis == null ? null : analysis.getSchoolsWithin1km();

        // no analysis, or an analysis without any value -> NO_COVERAGE
        if (distPublic == null && distCharter == null && within1km == null) {
            context.setStatus(SchoolContextDTO.STATUS_NO_COVERAGE);
            context.setNote(NO_COVERAGE_NOTE);
            return context;
        }

        // copy the stored values unchanged; part scores only for known values, since unknown ones are stored as 0
        context.setNearestPublicSchoolDistance(distPublic);
        context.setNearestCharterSchoolDistance(distCharter);
        context.setSchoolsWithin1km(within1km);
        context.setPublicSchoolDistanceScore(distPublic == null ? null : rounded(analysis.getPublicSchoolDistanceScore()));
        context.setCharterSchoolDistanceScore(distCharter == null ? null : rounded(analysis.getCharterSchoolDistanceScore()));
        context.setSchoolDensityScore(within1km == null ? null : rounded(analysis.getSchoolDensityScore()));

        List<String> unknown = new ArrayList<>();
        if (distPublic == null) unknown.add("nearestPublicSchoolDistance");
        if (distCharter == null) unknown.add("nearestCharterSchoolDistance");
        if (within1km == null) unknown.add("schoolsWithin1km");

        List<String> notes = new ArrayList<>();
        if (Integer.valueOf(0).equals(within1km)) {
            context.setStatus(SchoolContextDTO.STATUS_NO_NEARBY_RESULTS);
            notes.add(NO_NEARBY_NOTE);
        } else if (within1km == null && distPublic != null && distCharter != null
                && distPublic > NEARBY_DISTANCE && distCharter > NEARBY_DISTANCE) {
            // count unknown, but both known distances already show nothing nearby
            context.setStatus(SchoolContextDTO.STATUS_NO_NEARBY_RESULTS);
            notes.add(NO_NEARBY_PUBLIC_OR_CHARTER_NOTE);
        } else {
            context.setStatus(SchoolContextDTO.STATUS_AVAILABLE);
        }

        if (unknown.isEmpty()) {
            Integer score = rounded(analysis.getSchoolScore());
            context.setSchoolScore(score);
            context.setSchoolScoreLabel(label(score));
        } else {
            notes.add("Score unavailable, unknown values: " + String.join(", ", unknown));
        }
        context.setNote(notes.isEmpty() ? null : String.join(". ", notes));
        return context;
    }

    private static Integer rounded(Double value) {
        return value == null ? null : (int) Math.round(value);
    }

    static String label(Integer score) {
        if (score == null) return null;
        if (score >= 80) return SchoolContextDTO.LABEL_EXCELLENT;
        if (score >= 60) return SchoolContextDTO.LABEL_GOOD;
        if (score >= 40) return SchoolContextDTO.LABEL_FAIR;
        return SchoolContextDTO.LABEL_LIMITED;
    }
}
