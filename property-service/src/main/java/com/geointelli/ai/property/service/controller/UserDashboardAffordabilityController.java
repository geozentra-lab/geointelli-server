package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.AffordabilityCalculationRequest;
import com.geointelli.ai.property.service.dto.AffordabilityCardDTO;
import com.geointelli.ai.property.service.service.UserDashboardAffordabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Affordability card of the normal-user dashboard (SCRUM-84 / SCRUM-99). Each dashboard card has
 * its own endpoint under {@code /api/dashboards/user} so it can be verified alone; the composed
 * {@code GET /api/dashboards/user} is added by SCRUM-102.
 */
@RestController
@RequestMapping("/api/dashboards/user")
@RequiredArgsConstructor
@Tag(name = "User dashboard", description = "Bearer token identifies the user; every card shows that user's data only.")
public class UserDashboardAffordabilityController {

    private final UserDashboardAffordabilityService userDashboardAffordabilityService;

    /**
     * Affordability card: the affordability estimate for the supplied financial inputs.
     *
     * @param inputs annualGrossIncome, monthlyDebtPayments, downPayment, annualInterestRate (%), loanTermYears
     *               and optional annualPropertyTax, annualHomeInsurance, monthlyHoaFees as query parameters;
     *               all may be omitted
     * @return 200 with status OK, or INCOMPLETE listing the required inputs not supplied; 400 if a supplied
     *         input is out of range or not a number
     */
    @GetMapping("/affordability")
    @Operation(summary = "Affordability card of my dashboard", description = "Reuses the affordability calculator "
            + "(28/36 rule) on the supplied inputs and returns its assumptions, notes and disclaimer. No salary, debt, "
            + "rate or HOA value is assumed: when a required input is missing the card is INCOMPLETE and lists it in "
            + "missingInputs. No personal or organization record is read.")
    public ResponseEntity<AffordabilityCardDTO> affordability(@ParameterObject AffordabilityCalculationRequest inputs) {
        return ResponseEntity.ok(userDashboardAffordabilityService.getAffordabilityCard(inputs));
    }
}
