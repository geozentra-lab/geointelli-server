package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.RiskContextDTO;
import com.geointelli.ai.property.service.service.PropertyRiskContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Risk section of the property location intelligence (SCRUM-53 / SCRUM-127). Each section has its own
 * endpoint so it can be verified alone; the composed {@code GET /api/properties/id/{id}/location-intelligence}
 * is added by SCRUM-128.
 */
@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
@Tag(name = "Location intelligence", description = "Neighborhood, school, transport and risk context of a property.")
public class PropertyRiskContextController {

    private final PropertyRiskContextService propertyRiskContextService;

    /**
     * Risk section of the location intelligence of a property.
     *
     * @param id internal property ID
     * @return 200 with status AVAILABLE, NO_COVERAGE or UNAVAILABLE; 400 if the ID is invalid;
     *         404 if the property does not exist
     */
    @GetMapping("/id/{id}/location-intelligence/risk")
    @Operation(summary = "Risk context of a property", description = "Locates the property on its parcel polygon "
            + "and returns three independent blocks from the environmental reference data: the FEMA flood zone at "
            + "the point, the FEMA National Risk Index ratings of its census tract, and the NOAA storm events within "
            + "10 km since 1996. Each block carries its own status, source and download date; missing evidence is "
            + "NO_DATA or NO_COVERAGE, never a rating. NO_COVERAGE at the top means the property has no parcel "
            + "polygon; UNAVAILABLE means the reference data could not be read (200, error in the section).")
    public ResponseEntity<RiskContextDTO> getRiskContext(@PathVariable Long id) {
        return ResponseEntity.ok(propertyRiskContextService.getRiskContext(id));
    }
}
