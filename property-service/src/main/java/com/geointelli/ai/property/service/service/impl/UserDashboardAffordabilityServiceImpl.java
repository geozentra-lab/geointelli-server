package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.AffordabilityCalculationRequest;
import com.geointelli.ai.property.service.dto.AffordabilityCalculationResponse;
import com.geointelli.ai.property.service.dto.AffordabilityCardDTO;
import com.geointelli.ai.property.service.service.AffordabilityCalculationService;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.UserDashboardAffordabilityService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Requires a bearer token. Supplied inputs are validated with the {@link AffordabilityCalculationRequest}
 * constraints first; missing required inputs then give an INCOMPLETE card, otherwise the inputs are passed
 * unchanged to the affordability calculator.
 */
@Service
@RequiredArgsConstructor
public class UserDashboardAffordabilityServiceImpl implements UserDashboardAffordabilityService {

    static final String INCOMPLETE_ERROR = "Required financial inputs not supplied";
    private static final List<String> REQUIRED_INPUTS = List.of(
            "annualGrossIncome", "monthlyDebtPayments", "downPayment", "annualInterestRate", "loanTermYears");

    private final AffordabilityCalculationService affordabilityCalculationService;
    private final CurrentUserService currentUserService;
    private final Validator validator;

    @Override
    public AffordabilityCardDTO getAffordabilityCard(AffordabilityCalculationRequest inputs) {
        // A user dashboard card: 401 without a bearer token, like the other cards (no user row is read or written)
        currentUserService.getCurrentKeycloakUserId();

        // Supplied values are checked first, so an out-of-range input is a 400 even when others are missing;
        // missing required inputs (@NotNull) are reported below as INCOMPLETE instead
        if (inputs != null) {
            Set<ConstraintViolation<AffordabilityCalculationRequest>> violations = validator.validate(inputs).stream()
                    .filter(violation -> !(violation.getConstraintDescriptor().getAnnotation() instanceof NotNull))
                    .collect(Collectors.toSet());
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .sorted()
                        .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(message);
            }
        }

        // Required inputs are never assumed: any missing one gives an INCOMPLETE card
        List<String> missingInputs = new ArrayList<>();

        if (inputs == null) {
            missingInputs.addAll(REQUIRED_INPUTS);
        } else {
            if (inputs.annualGrossIncome() == null) missingInputs.add("annualGrossIncome");
            if (inputs.monthlyDebtPayments() == null) missingInputs.add("monthlyDebtPayments");
            if (inputs.downPayment() == null) missingInputs.add("downPayment");
            if (inputs.annualInterestRate() == null) missingInputs.add("annualInterestRate");
            if (inputs.loanTermYears() == null) missingInputs.add("loanTermYears");
        }

        if (!missingInputs.isEmpty()) {
            return card(AffordabilityCardDTO.STATUS_INCOMPLETE, INCOMPLETE_ERROR, missingInputs, null);
        }

        // Reused unchanged: assumptions (28/36 ratios, rate, term, rate source), notes and disclaimer included
        AffordabilityCalculationResponse affordability = affordabilityCalculationService.calculateAffordability(inputs);
        return card(AffordabilityCardDTO.STATUS_OK, null, List.of(), affordability);
    }

    private static AffordabilityCardDTO card(String status, String error, List<String> missingInputs,
                                             AffordabilityCalculationResponse affordability) {
        AffordabilityCardDTO card = new AffordabilityCardDTO();
        card.setScope(AffordabilityCardDTO.SCOPE_CURRENT_USER);
        card.setGeneratedAt(LocalDateTime.now());
        card.setStatus(status);
        card.setError(error);
        card.setMissingInputs(missingInputs);
        card.setAffordability(affordability);
        return card;
    }
}
