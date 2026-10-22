package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;
import com.geointelli.ai.property.service.dto.MortgageCalculationResponse;
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



}
