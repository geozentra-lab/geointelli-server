package com.geointelli.ai.property.service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.PropertyCompareRequest;
import com.geointelli.ai.property.service.dto.PropertyCompareResponse;
import com.geointelli.ai.property.service.service.PropertyCompareService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Compares user-selected properties side by side. */
@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyCompareController {
    private final PropertyCompareService propertyCompareService;

    /**
     * Compares the given properties using their stored facts, valuation and scores.
     *
     * @param request {@value PropertyCompareRequest#MIN_PROPERTIES} to
     *                {@value PropertyCompareRequest#MAX_PROPERTIES} distinct internal property IDs
     * @return 200 with one column per ID in request order, unavailable values null and listed in
     *         {@code missingFields}; 400 if the list is missing, out of bounds, has invalid or
     *         duplicate IDs; 404 if any ID does not match a property
     */
    @PostMapping("/compare")
    public ResponseEntity<PropertyCompareResponse> compare(@Valid @RequestBody PropertyCompareRequest request) {
        return ResponseEntity.ok(propertyCompareService.compare(request));
    }
}
