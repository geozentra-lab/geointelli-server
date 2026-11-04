package com.geointelli.ai.property.service.controller;


import com.geointelli.ai.property.service.dto.SchoolContextDTO;
import com.geointelli.ai.property.service.service.PropertySchoolContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Location intelligence of a property (SCRUM-53). Each section has its own endpoint so it can be verified
 * alone; the composed {@code GET /api/properties/id/{id}/location-intelligence} is added by SCRUM-128.
 */
@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
@Tag(name = "Location intelligence", description = "Neighborhood, school, transport and risk context of a property.")
public class PropertyLocationIntelligenceController {

    private final PropertySchoolContextService propertySchoolContextService;

    /**
     * School section of the location intelligence of a property.
     *
     * @param id internal property ID
     * @return 200 with status AVAILABLE, NO_NEARBY_RESULTS or NO_COVERAGE; 400 if the ID is invalid;
     *         404 if the property does not exist
     */
    @GetMapping("/id/{id}/location-intelligence/schools")
    @Operation(summary = "School context of a property", description = "Reads the stored school analysis of the "
            + "property: distances to the nearest public and charter schools, number of schools within 1 km and "
            + "school score with its label and parts (null when any value is unknown), with distance unit and source. NO_COVERAGE means no school data for the "
            + "property; NO_NEARBY_RESULTS means data exists but no school is within 1 km.")
    public ResponseEntity<SchoolContextDTO> getSchoolContext(@PathVariable Long id) {
        return ResponseEntity.ok(propertySchoolContextService.getSchoolContext(id));
    }


}
