package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.dto.SchoolContextDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.PropertySchoolContextService;
import com.geointelli.ai.property.service.service.SchoolAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the stored school analysis of the property through {@link SchoolAnalysisService} (nothing is
 * recalculated or saved) and labels it AVAILABLE, NO_NEARBY_RESULTS or NO_COVERAGE from the raw values,
 * never from the score, because the score turns unknown values into 0.
 */
@Service
@RequiredArgsConstructor
public class PropertySchoolContextServiceImpl implements PropertySchoolContextService {

    static final String NO_COVERAGE_NOTE = "No school data available for this property";
    static final String NO_NEARBY_NOTE = "No school within 1 km";

    private final PropertyRepository propertyRepository;
    private final SchoolAnalysisService schoolAnalysisService;

    @Override
    public SchoolContextDTO getSchoolContext(Long propertyId) {
        // invalid ID -> 400, unknown property -> 404
        if (propertyId == null || propertyId <= 0) {
            throw new IllegalArgumentException("id must be a positive number");
        }
        if (!propertyRepository.existsById(propertyId)) {
            throw new PropertyNotFoundException("No property found with id: " + propertyId);
        }

        // reuse the existing school service (read only)
        SchoolAnalysisDTO analysis = schoolAnalysisService.getPropertySchoolAnalysisByIds(List.of(propertyId))
                .stream().findFirst().orElse(null);

        SchoolContextDTO context = new SchoolContextDTO();
        context.setPropertyId(propertyId);
        context.setDistanceUnit(SchoolContextDTO.DISTANCE_UNIT);
        context.setSource(SchoolContextDTO.SOURCE);

        // no analysis, or an analysis without any value -> NO_COVERAGE
        if (analysis == null || (analysis.getDistPublicSchool() == null
                && analysis.getDistCharterSchool() == null
                && analysis.getSchoolsWithin1km() == null)) {
            context.setStatus(SchoolContextDTO.STATUS_NO_COVERAGE);
            context.setNote(NO_COVERAGE_NOTE);
            return context;
        }

        // copy the stored values unchanged; 0 schools within 1 km -> NO_NEARBY_RESULTS
        context.setNearestPublicSchoolDistance(analysis.getDistPublicSchool());
        context.setNearestCharterSchoolDistance(analysis.getDistCharterSchool());
        context.setSchoolsWithin1km(analysis.getSchoolsWithin1km());
        context.setSchoolScore(analysis.getSchoolScore());
        context.setDataUpdatedAt(analysis.getUpdatedAt());

        if (Integer.valueOf(0).equals(analysis.getSchoolsWithin1km())) {
            context.setStatus(SchoolContextDTO.STATUS_NO_NEARBY_RESULTS);
            context.setNote(NO_NEARBY_NOTE);
            return context;
        }

        context.setStatus(SchoolContextDTO.STATUS_AVAILABLE);
        List<String> unknown = new ArrayList<>();
        if (analysis.getDistPublicSchool() == null) unknown.add("nearestPublicSchoolDistance");
        if (analysis.getDistCharterSchool() == null) unknown.add("nearestCharterSchoolDistance");
        if (analysis.getSchoolsWithin1km() == null) unknown.add("schoolsWithin1km");
        if (!unknown.isEmpty()) {
            context.setNote("Unknown values: " + String.join(", ", unknown));
        }
        return context;
    }
}
