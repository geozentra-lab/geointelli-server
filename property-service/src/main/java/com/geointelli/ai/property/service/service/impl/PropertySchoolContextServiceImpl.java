package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.AssignedSchoolDTO;
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
import java.util.Objects;

/**
 * Reads the stored school data of the property through {@link SchoolAnalysisService} (nothing is saved). A property
 * with assigned schools (SCRUM-203) is AVAILABLE with its assigned schools and school score v2; otherwise the v1 raw
 * values label it AVAILABLE, NO_NEARBY_RESULTS or NO_COVERAGE, never the score. The v1 score is the school formula
 * over the known part scores only, so rows scored before unknown values were left out still get the right score.
 */
@Service
@RequiredArgsConstructor
public class PropertySchoolContextServiceImpl implements PropertySchoolContextService {

    static final String NO_COVERAGE_NOTE = "No school data available for this property";
    static final String NO_NEARBY_NOTE = "No school within 1 km";
    static final String NO_NEARBY_PUBLIC_OR_CHARTER_NOTE = "No public or charter school within 1 km";
    static final String NO_ASSIGNED_SCHOOL_NOTE = "No school assigned for: ";
    static final String NEAREST_NOTE = "Nearest zoned public school shown, no attendance zone covers this property for: ";
    static final String UNMATCHED_ZONE_NOTE = "Zoned school not in the school data, left out of the score, for: ";
    static final String SCORE_V2_PENDING_NOTE = "School score v2 not computed yet";
    static final String SCORE_V2_UNAVAILABLE_NOTE = "School score v2 unavailable: no assigned school is known";
    static final String V1_NOTE_PREFIX = "v1 fields: ";
    static final List<String> LEVELS = List.of("ELEMENTARY", "MIDDLE", "HIGH");
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
        boolean hasV1 = distPublic != null || distCharter != null || within1km != null;
        boolean hasV2 = analysis != null && analysis.getAssignedSchools() != null
                && !analysis.getAssignedSchools().isEmpty();

        // no v2 assignment and no v1 value -> NO_COVERAGE
        if (!hasV1 && !hasV2) {
            context.setStatus(SchoolContextDTO.STATUS_NO_COVERAGE);
            context.setNote(NO_COVERAGE_NOTE);
            return context;
        }
        List<String> notes = new ArrayList<>();
        if (hasV2) {
            // the assigned schools describe the property whatever the v1 values say
            context.setStatus(SchoolContextDTO.STATUS_AVAILABLE);
            notes.addAll(addScoreV2(context, analysis));
        }
        if (!hasV1) {
            context.setNote(notes.isEmpty() ? null : String.join(". ", notes));
            return context;
        }

        // copy the stored values unchanged; part scores only for known values, since older rows store unknown ones as 0
        Double publicPart = distPublic == null ? null : analysis.getPublicSchoolDistanceScore();
        Double charterPart = distCharter == null ? null : analysis.getCharterSchoolDistanceScore();
        Double densityPart = within1km == null ? null : analysis.getSchoolDensityScore();
        context.setNearestPublicSchoolDistance(distPublic);
        context.setNearestCharterSchoolDistance(distCharter);
        context.setSchoolsWithin1km(within1km);
        context.setPublicSchoolDistanceScore(rounded(publicPart));
        context.setCharterSchoolDistanceScore(rounded(charterPart));
        context.setSchoolDensityScore(rounded(densityPart));

        List<String> unknown = new ArrayList<>();
        if (distPublic == null) unknown.add("nearestPublicSchoolDistance");
        if (distCharter == null) unknown.add("nearestCharterSchoolDistance");
        if (within1km == null) unknown.add("schoolsWithin1km");

        List<String> v1Notes = new ArrayList<>();
        String v1Status = SchoolContextDTO.STATUS_AVAILABLE;
        if (Integer.valueOf(0).equals(within1km)) {
            v1Status = SchoolContextDTO.STATUS_NO_NEARBY_RESULTS;
            v1Notes.add(NO_NEARBY_NOTE);
        } else if (within1km == null && distPublic != null && distCharter != null
                && distPublic > NEARBY_DISTANCE && distCharter > NEARBY_DISTANCE) {
            // count unknown, but both known distances already show nothing nearby
            v1Status = SchoolContextDTO.STATUS_NO_NEARBY_RESULTS;
            v1Notes.add(NO_NEARBY_PUBLIC_OR_CHARTER_NOTE);
        }
        if (!hasV2) {
            context.setStatus(v1Status);
        }

        Integer score = rounded(SchoolAnalysisServiceImpl.schoolScore(publicPart, charterPart, densityPart));
        context.setSchoolScore(score);
        context.setSchoolScoreLabel(label(score));
        if (!unknown.isEmpty()) {
            v1Notes.add((score == null ? "Score unavailable" : "Score based on known values only")
                    + ", unknown values: " + String.join(", ", unknown));
        }
        // next to the assigned schools, say that these notes are about the deprecated v1 fields
        v1Notes.forEach(note -> notes.add(hasV2 ? V1_NOTE_PREFIX + note : note));
        context.setNote(notes.isEmpty() ? null : String.join(". ", notes));
        return context;
    }

    /**
     * Copies the assigned schools and school score v2 into the context and sets the v2 source; returns the notes on
     * what v2 could not know.
     */
    private static List<String> addScoreV2(SchoolContextDTO context, SchoolAnalysisDTO analysis) {
        List<AssignedSchoolDTO> schools = analysis.getAssignedSchools();
        context.setAssignedSchools(schools);
        context.setQualityScore(rounded(analysis.getQualityScore()));
        context.setAccessScore(rounded(analysis.getAccessScore()));
        context.setChoiceScore(rounded(analysis.getChoiceScore()));
        context.setSchoolScoreV2(rounded(analysis.getSchoolScoreV2()));
        context.setSchoolScoreV2Label(analysis.getSchoolScoreV2Label());
        context.setScoreVersion(analysis.getScoreVersion());
        Integer gradeYear = analysis.getGradeYear() != null ? analysis.getGradeYear()
                : schools.stream().map(AssignedSchoolDTO::getGradeYear).filter(Objects::nonNull).findFirst().orElse(null);
        context.setGradeYear(gradeYear);
        context.setSource(gradeYear == null ? SchoolContextDTO.SOURCE_V2
                : "FLDOE School Grades " + gradeYear + ", " + SchoolContextDTO.SOURCE_V2);

        List<String> notes = new ArrayList<>();
        List<String> missing = LEVELS.stream()
                .filter(level -> schools.stream().noneMatch(school -> level.equals(school.getLevel()))).toList();
        if (!missing.isEmpty()) {
            notes.add(NO_ASSIGNED_SCHOOL_NOTE + String.join(", ", missing));
        }
        List<String> unmatched = levelsAssignedBy(schools, AssignedSchoolDTO.METHOD_UNMATCHED_ZONE);
        if (!unmatched.isEmpty()) {
            notes.add(UNMATCHED_ZONE_NOTE + String.join(", ", unmatched));
        }
        List<String> nearest = levelsAssignedBy(schools, AssignedSchoolDTO.METHOD_NEAREST);
        if (!nearest.isEmpty()) {
            notes.add(NEAREST_NOTE + String.join(", ", nearest));
        }
        if (analysis.getSchoolScoreV2() == null) {
            // a stored row without a score: no level has a known school
            notes.add(analysis.getScoreVersion() == null ? SCORE_V2_PENDING_NOTE : SCORE_V2_UNAVAILABLE_NOTE);
        } else {
            List<String> unknown = new ArrayList<>();
            if (analysis.getQualityScore() == null) unknown.add("qualityScore");
            if (analysis.getAccessScore() == null) unknown.add("accessScore");
            if (analysis.getChoiceScore() == null) unknown.add("choiceScore");
            if (!unknown.isEmpty()) {
                notes.add("School score v2 based on known parts only, unknown parts: " + String.join(", ", unknown));
            }
        }
        return notes;
    }

    private static List<String> levelsAssignedBy(List<AssignedSchoolDTO> schools, String method) {
        return schools.stream().filter(school -> method.equals(school.getAssignmentMethod()))
                .map(AssignedSchoolDTO::getLevel).toList();
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
