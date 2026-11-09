package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.*;
import com.geointelli.ai.property.service.service.AffordabilityCalculationService;
import com.geointelli.ai.property.service.service.InvestmentAnalysisService;
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

    private final InvestmentAnalysisService investmentAnalysisService;

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

    /**
     * Analyzes a residential investment scenario: unfinanced operating result (NOI), financed metrics
     * (debt service, cash flow, cap rate, cash-on-cash), gross yield and, when requested, a labelled
     * equity projection. Missing inputs are never filled in: the values that need them are null with a note.
     *
     * @param request operating inputs (monthly rent required), optional financing and optional projection
     * @return 200 with current returns, projection and notes;
     *         400 if the rent is missing, a field is invalid or the down payment exceeds the price
     */
    @PostMapping("/investment")
    public ResponseEntity<InvestmentAnalysisResponse> analyzeInvestment(@Valid @RequestBody InvestmentAnalysisRequest request) {
        InvestmentAnalysisResponse response = investmentAnalysisService.analyze(request);
        return ResponseEntity.ok(response);
    }
}
