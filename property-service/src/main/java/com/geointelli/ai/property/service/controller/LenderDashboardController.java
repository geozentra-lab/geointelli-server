package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.MortgageContextCardDTO;
import com.geointelli.ai.property.service.service.LenderDashboardMortgageContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;

/**
 * Bank or lender dashboard (SCRUM-87). Each card has its own endpoint so it can be verified alone;
 * the composed lender dashboard is added by SCRUM-117.
 */
@RestController
@RequestMapping("/api/dashboards/lender")
@RequiredArgsConstructor
@Tag(name = "Lender dashboard", description = "Cards of the bank or lender dashboard.")
public class LenderDashboardController {

    private final LenderDashboardMortgageContextService mortgageContextService;

    /**
     * Mortgage context card: the mortgage calculation for the supplied inputs.
     *
     * @param inputs price, downPayment, annualInterestRate (%), loanTermYears and optional
     *               annualPropertyTax, annualHomeInsurance, monthlyHoaFees as query parameters; all may be omitted
     * @return 200 with status OK, or UNAVAILABLE when no input is supplied; 400 if inputs are
     *         partial, out of range, not a number, or the down payment exceeds the price
     */
    @GetMapping("/mortgage-context")
    @Operation(summary = "Mortgage context card of the lender dashboard", description = "Reuses the mortgage "
            + "calculator on the supplied inputs and discloses the rate source and calculation date. "
            + "No live mortgage-rate feed is used. Without any input the card is UNAVAILABLE.")
    public ResponseEntity<MortgageContextCardDTO> mortgageContext(@ParameterObject MortgageCalculationRequest inputs) {
        return ResponseEntity.ok(mortgageContextService.getMortgageContextCard(inputs));
    }

}
