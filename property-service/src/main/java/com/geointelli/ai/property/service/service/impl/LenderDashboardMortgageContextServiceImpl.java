package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;
import com.geointelli.ai.property.service.dto.MortgageCalculationResponse;
import com.geointelli.ai.property.service.dto.MortgageContextCardDTO;
import com.geointelli.ai.property.service.dto.RateAssumptions;
import com.geointelli.ai.property.service.service.LenderDashboardMortgageContextService;
import com.geointelli.ai.property.service.service.MortgageCalculationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static com.geointelli.ai.property.service.dto.MortgageContextCardDTO.*;

/**
 * Empty inputs give an UNAVAILABLE card; otherwise the inputs are validated with the
 * {@link MortgageCalculationRequest} constraints and passed unchanged to the mortgage calculator.
 */
@Service
@RequiredArgsConstructor
public class LenderDashboardMortgageContextServiceImpl implements LenderDashboardMortgageContextService {

    static final String NO_INPUT_ERROR = "No mortgage inputs supplied";

    private final MortgageCalculationService mortgageCalculationService;
    private final Validator validator;   // jakarta.validation.Validator

    @Override
    public MortgageContextCardDTO getMortgageContextCard(MortgageCalculationRequest inputs) {
        LocalDateTime generatedAt = LocalDateTime.now();
        if (hasNoInput(inputs)) {
            return new MortgageContextCardDTO(SCOPE_SUPPLIED_INPUTS, generatedAt, STATUS_UNAVAILABLE,
                    NO_INPUT_ERROR, null, null);
        }

        // Same rules as POST /api/calculations/mortgage; violations become a 400
        Set<ConstraintViolation<MortgageCalculationRequest>> violations = validator.validate(inputs);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(message);
        }

        // Throws IllegalArgumentException when the down payment exceeds the price
        MortgageCalculationResponse mortgage = mortgageCalculationService.calculateMortgage(inputs);

        RateAssumptions assumptions = new RateAssumptions(
                inputs.annualInterestRate(), inputs.loanTermYears(), RATE_SOURCE);

        return new MortgageContextCardDTO(SCOPE_SUPPLIED_INPUTS, generatedAt, STATUS_OK,
                null, assumptions, mortgage);
    }


    private static boolean hasNoInput(MortgageCalculationRequest inputs) {
        return inputs == null
                || (inputs.price() == null
                && inputs.downPayment() == null
                && inputs.annualInterestRate() == null
                && inputs.loanTermYears() == null
                && inputs.annualPropertyTax() == null
                && inputs.annualHomeInsurance() == null
                && inputs.monthlyHoaFees() == null);
    }
}
