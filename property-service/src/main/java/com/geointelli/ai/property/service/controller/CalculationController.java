package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.AffordabilityCalculationRequest;
import com.geointelli.ai.property.service.dto.AffordabilityCalculationResponse;
import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;
import com.geointelli.ai.property.service.dto.MortgageCalculationResponse;
import com.geointelli.ai.property.service.service.AffordabilityCalculationService;
import com.geointelli.ai.property.service.service.MortgageCalculationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calculations")
@RequiredArgsConstructor
public class CalculationController {

    private final MortgageCalculationService mortgageCalculationService;

    private final AffordabilityCalculationService affordabilityCalculationService;

    /**
     * Calculates the monthly mortgage payment and itemized ownership costs.
     *
     * @param request price, down payment, annual rate (%), term (years) and optional tax, insurance and HOA
     * @return 200 with the cost breakdown (unsupplied costs are null);
     *         400 if a field is missing or invalid, or the down payment exceeds the price
     */
    @PostMapping("/mortgage")
    public ResponseEntity<MortgageCalculationResponse> calculateMortgage(@Valid @RequestBody MortgageCalculationRequest request) {
        MortgageCalculationResponse response = mortgageCalculationService.calculateMortgage(request);
        return ResponseEntity.ok(response);
    }


    /**
     * Estimates how much a buyer can afford using the 28/36 rule and the shared mortgage formula.
     * The result is an estimate, not a loan approval.
     *
     * @param request yearly gross income, monthly debts, down payment, annual rate (%), term (years)
     *                and optional yearly tax/insurance, monthly HOA
     * @return 200 with the estimate, the assumptions used and notes on missing input;
     *         400 if a field is missing or invalid
     */
    @PostMapping("/affordability")
    public ResponseEntity<AffordabilityCalculationResponse> calculateAffordability(@Valid @RequestBody AffordabilityCalculationRequest request) {
        AffordabilityCalculationResponse response = affordabilityCalculationService.calculateAffordability(request);
        return ResponseEntity.ok(response);
    }
}
