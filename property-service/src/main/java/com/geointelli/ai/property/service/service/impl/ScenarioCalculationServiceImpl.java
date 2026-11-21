package com.geointelli.ai.property.service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.InvestmentAnalysisRequest;
import com.geointelli.ai.property.service.dto.InvestmentAnalysisResponse;
import com.geointelli.ai.property.service.dto.ScenarioCalculationDTO;
import com.geointelli.ai.property.service.dto.ScenarioCalculationDTO.CurrentFacts;
import com.geointelli.ai.property.service.dto.ScenarioCalculationDTO.InputChange;
import com.geointelli.ai.property.service.dto.ScenarioDTO;
import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.entity.ScenarioType;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.service.InvestmentAnalysisService;
import com.geointelli.ai.property.service.service.ScenarioCalculationService;
import com.geointelli.ai.property.service.service.ScenarioService;
import com.geointelli.ai.property.service.service.ValuationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the scenario through {@link ScenarioService} (same owner check and 404), turns its assumptions into the
 * request of {@code POST /api/calculations/investment}, validates it with the same rules and calls the shared
 * {@link InvestmentAnalysisService}: no formula is copied here.
 */
@Service
@RequiredArgsConstructor
public class ScenarioCalculationServiceImpl implements ScenarioCalculationService {

    static final int SUPPORTED_INPUT_VERSION = 1;

    private final ScenarioService scenarioService;
    private final InvestmentAnalysisService investmentAnalysisService;
    private final ValuationService valuationService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Override
    public ScenarioCalculationDTO calculate(Long id) {
        // Owner check, positive ID and 404 come from the saved scenario service
        ScenarioDTO scenario = scenarioService.get(id);
        if (scenario.scenarioType() != ScenarioType.INVESTMENT) {
            throw new IllegalArgumentException("Only INVESTMENT scenarios can be recalculated");
        }
        if (scenario.inputVersion() != SUPPORTED_INPUT_VERSION) {
            throw new IllegalArgumentException("Unsupported assumptions version: " + scenario.inputVersion());
        }

        // The saved assumptions are the only inputs of the shared calculation
        InvestmentAnalysisRequest request = toRequest(scenario.assumptions());
        InvestmentAnalysisResponse result = investmentAnalysisService.analyze(request);

        // Current facts are read for comparison only
        CurrentFacts facts = currentFacts(scenario.propertyId());
        List<InputChange> changes = inputChanges(request, facts);

        return new ScenarioCalculationDTO(scenario.id(), scenario.name(), scenario.scenarioType(),
                scenario.inputVersion(), scenario.assumptions(), facts, changes, result, ScenarioCalculationDTO.NOTE);
    }

    /** Converts and validates the saved assumptions with the rules of the investment calculation endpoint. */
    private InvestmentAnalysisRequest toRequest(JsonNode assumptions) {
        InvestmentAnalysisRequest request;
        try {
            request = objectMapper.treeToValue(assumptions, InvestmentAnalysisRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Saved assumptions are invalid: they do not match the investment calculation input");
        }
        Set<ConstraintViolation<InvestmentAnalysisRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Saved assumptions are invalid: " + details);
        }
        return request;
    }

    /** The linked property's current valuation, or why it is unavailable. */
    private CurrentFacts currentFacts(Long propertyId) {
        if (propertyId == null) {
            return new CurrentFacts(null, ScenarioCalculationDTO.NO_LINKED_PROPERTY, null, null, null, null);
        }
        ValuationResponse valuation;
        try {
            valuation = valuationService.getValuationWithoutComparables(propertyId);
        } catch (PropertyNotFoundException e) {
            return new CurrentFacts(propertyId, ScenarioCalculationDTO.PROPERTY_NOT_FOUND, null, null, null, null);
        }
        ValuationResponse.Estimate estimate = valuation.estimate();
        if ("unsupported".equals(valuation.status()) || estimate == null || estimate.value() == null) {
            return new CurrentFacts(propertyId, ScenarioCalculationDTO.VALUATION_UNAVAILABLE, null,
                    valuation.status(), null, null);
        }
        return new CurrentFacts(propertyId, ScenarioCalculationDTO.FACTS_AVAILABLE, estimate.value(),
                valuation.status(), estimate.confidence(),
                valuation.method() == null ? null : valuation.method().predictionDate());
    }

    /** Saved inputs that differ from the current facts or cannot be compared with them. */
    private static List<InputChange> inputChanges(InvestmentAnalysisRequest request, CurrentFacts facts) {
        List<InputChange> changes = new ArrayList<>();
        if (ScenarioCalculationDTO.PROPERTY_NOT_FOUND.equals(facts.status())) {
            changes.add(new InputChange("propertyId", null, null,
                    "The linked property no longer exists; current facts are unavailable."));
        }
        BigDecimal savedPrice = request.financing() == null ? null : request.financing().purchasePrice();
        if (savedPrice != null) {
            if (ScenarioCalculationDTO.FACTS_AVAILABLE.equals(facts.status())
                    && savedPrice.compareTo(facts.estimatedValue()) != 0) {
                changes.add(new InputChange("financing.purchasePrice", savedPrice, facts.estimatedValue(),
                        "The saved purchase price differs from the property's current estimated value. "
                                + "The calculation uses the saved price."));
            } else if (ScenarioCalculationDTO.VALUATION_UNAVAILABLE.equals(facts.status())) {
                changes.add(new InputChange("financing.purchasePrice", savedPrice, null,
                        "No current valuation is available to compare with the saved purchase price."));
            }
        }
        return List.copyOf(changes);
    }
}
