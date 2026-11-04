package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.TransportContextDTO;
import com.geointelli.ai.property.service.service.PropertyTransportContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transport section of the property location intelligence (SCRUM-53 / SCRUM-126). Each section has its own
 * endpoint so it can be verified alone; the composed {@code GET /api/properties/id/{id}/location-intelligence}
 * is added by SCRUM-128.
 */
@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
@Tag(name = "Location intelligence", description = "Neighborhood, school, transport and risk context of a property.")
public class PropertyTransportContextController {
    private final PropertyTransportContextService propertyTransportContextService;

    /**
     * Transport section of the location intelligence of a property.
     *
     * @param id internal property ID
     * @return 200 with status AVAILABLE, NO_COVERAGE or UNAVAILABLE; 400 if the ID is invalid;
     *         404 if the property does not exist
     */
    @GetMapping("/id/{id}/location-intelligence/transport")
    @Operation(summary = "Transport context of a property", description = "Reads the stored transportation analysis "
            + "of the property and returns the distance to the nearest RAIL, PEOPLE_MOVER, BUS and HIGHWAY access, "
            + "the number of bus stops within 500 m and the transportation score over the known values only, with "
            + "its label, distance unit and source. NO_COVERAGE means no transport data for the property; UNAVAILABLE means the transport "
            + "data source failed (200, error in the section).")
    public ResponseEntity<TransportContextDTO> getTransportContext(@PathVariable Long id) {
        return ResponseEntity.ok(propertyTransportContextService.getTransportContext(id));
    }
}
