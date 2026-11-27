package com.geointelli.ai.property.service.controller;


import com.geointelli.ai.property.service.dto.LocationIntelligenceDTO;
import com.geointelli.ai.property.service.dto.SchoolContextDTO;
import com.geointelli.ai.property.service.service.PropertyLocationIntelligenceService;
import com.geointelli.ai.property.service.service.PropertySchoolContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Location intelligence of a property (SCRUM-53). Each section has its own endpoint so it can be verified
 * alone; {@code GET /api/properties/id/{id}/location-intelligence} (SCRUM-128) returns the school, transport and
 * risk sections together. The data describes places, not users: any authenticated caller may read any property.
 */
@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
@Tag(name = "Location intelligence", description = "Neighborhood, school, transport and risk context of a property.")
public class PropertyLocationIntelligenceController {

    private final PropertySchoolContextService propertySchoolContextService;
    private final PropertyLocationIntelligenceService locationIntelligenceService;

    /**
     * Location intelligence of a property: the school, transport and risk sections in one response.
     *
     * @param id internal property ID
     * @return 200 with status COMPLETE, PARTIAL (failed sections listed in errors, the others returned) or
     *         UNAVAILABLE (no section could be read); 400 if the ID is invalid; 404 if the property does not exist
     */
    @GetMapping("/id/{id}/location-intelligence")
    @Operation(summary = "Location intelligence of a property", description = "Returns the schools, transport and "
            + "risk sections, each exactly as its own endpoint (/location-intelligence/schools, /transport, /risk). "
            + "The sections are read independently: a section that fails is null and listed in errors with code "
            + "SECTION_FAILED; a section whose data source is down keeps its UNAVAILABLE status and is listed with "
            + "code SECTION_UNAVAILABLE. status is COMPLETE, PARTIAL or UNAVAILABLE. NO_COVERAGE inside a section "
            + "is an answer, not an error. The neighborhood section is not built yet.")
    @ApiResponse(responseCode = "200", description = "The sections, complete or partial")
    @ApiResponse(responseCode = "400", description = "The ID is not a positive number")
    @ApiResponse(responseCode = "401", description = "No token")
    @ApiResponse(responseCode = "404", description = "No property has this ID")
    public ResponseEntity<LocationIntelligenceDTO> getLocationIntelligence(@PathVariable Long id) {
        return ResponseEntity.ok(locationIntelligenceService.getLocationIntelligence(id));
    }

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
            + "school score with its label and parts (over the known values only), with distance unit and source. NO_COVERAGE means no school data for the "
            + "property; NO_NEARBY_RESULTS means data exists but no school is within 1 km.")
    public ResponseEntity<SchoolContextDTO> getSchoolContext(@PathVariable Long id) {
        return ResponseEntity.ok(propertySchoolContextService.getSchoolContext(id));
    }


}
