package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.ComparableCandidateRequest;
import com.geointelli.ai.property.service.dto.ComparableCandidateResponse;
import com.geointelli.ai.property.service.service.ComparableCandidateService;

import jakarta.validation.Valid;

/** Exposes candidate retrieval separately from the final comparables ranking. */
@RestController
@RequestMapping("/api/properties")
public class ComparableCandidateController {
    private final ComparableCandidateService comparableCandidateService;

    public ComparableCandidateController(ComparableCandidateService comparableCandidateService) {
        this.comparableCandidateService = comparableCandidateService;
    }

    @GetMapping("/id/{propertyId}/comparables/candidates")
    public ComparableCandidateResponse getCandidates(@PathVariable Long propertyId,
            @Valid @ModelAttribute ComparableCandidateRequest request) {
        return comparableCandidateService.findCandidates(propertyId, request);
    }
}
