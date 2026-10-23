package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.ComparableCandidateRequest;
import com.geointelli.ai.property.service.dto.ComparableResponse;
import com.geointelli.ai.property.service.service.ComparableService;

import jakarta.validation.Valid;

/** Returns ranked comparable transactions for a property. */
@RestController
@RequestMapping("/api/properties")
public class ComparableController {
    private final ComparableService comparableService;

    public ComparableController(ComparableService comparableService) {
        this.comparableService = comparableService;
    }

    @GetMapping("/id/{propertyId}/comparables")
    public ComparableResponse getComparables(@PathVariable Long propertyId,
            @Valid @ModelAttribute ComparableCandidateRequest request) {
        return comparableService.findComparables(propertyId, request);
    }
}
