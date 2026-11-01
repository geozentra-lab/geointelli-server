package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.AffordabilityAssumptions;
import com.geointelli.ai.property.service.dto.AffordabilityCalculationRequest;
import com.geointelli.ai.property.service.dto.AffordabilityCalculationResponse;
import com.geointelli.ai.property.service.service.AffordabilityCalculationService;
import com.geointelli.ai.property.service.util.MortgageMath;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class AffordabilityCalculationServiceImpl implements AffordabilityCalculationService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    // 28/36 rule: housing <= 28% and housing + debts <= 36% of gross monthly income
    private static final BigDecimal FRONT_END_RATIO_PERCENT = BigDecimal.valueOf(28);
    private static final BigDecimal BACK_END_RATIO_PERCENT = BigDecimal.valueOf(36);

    private static final String DISCLAIMER = "This is an affordability estimate, not a loan approval.";
    private static final String RATE_SOURCE = "Supplied by user, not a market rate";

    @Override
    public AffordabilityCalculationResponse calculateAffordability(AffordabilityCalculationRequest request) {
        List<String> notes = new ArrayList<>();

        BigDecimal monthlyGrossIncome = request.annualGrossIncome().divide(TWELVE, MC);

        BigDecimal frontEndLimit = percentOf(monthlyGrossIncome, FRONT_END_RATIO_PERCENT);
        BigDecimal backEndLimit = percentOf(monthlyGrossIncome, BACK_END_RATIO_PERCENT)
                .subtract(request.monthlyDebtPayments());

        BigDecimal maxMonthlyHousingPayment = frontEndLimit.min(backEndLimit);
        if (maxMonthlyHousingPayment.signum() <= 0) {
            notes.add("Existing debts reach or exceed the 36% back-end limit: no additional housing payment is affordable.");
            maxMonthlyHousingPayment = BigDecimal.ZERO;
        }

        // Only supplied costs are deducted; missing ones are reported, never assumed
        BigDecimal knownMonthlyCosts = BigDecimal.ZERO;

        if (request.annualPropertyTax() == null) {
            notes.add("Property tax not provided: not deducted from the budget, the estimate may be too high.");
        } else {
            knownMonthlyCosts = knownMonthlyCosts.add(request.annualPropertyTax().divide(TWELVE, MC));
        }

        if (request.annualHomeInsurance() == null) {
            notes.add("Home insurance not provided: not deducted from the budget, the estimate may be too high.");
        } else {
            knownMonthlyCosts = knownMonthlyCosts.add(request.annualHomeInsurance().divide(TWELVE, MC));
        }

        if (request.monthlyHoaFees() == null) {
            notes.add("HOA fees not provided: not deducted from the budget, the estimate may be too high.");
        } else {
            knownMonthlyCosts = knownMonthlyCosts.add(request.monthlyHoaFees());
        }

        BigDecimal maxMonthlyPrincipalAndInterest = maxMonthlyHousingPayment.subtract(knownMonthlyCosts);
        if (maxMonthlyPrincipalAndInterest.signum() < 0) {
            notes.add("Property tax, insurance and HOA fees exceed the housing budget: no loan is affordable.");
            maxMonthlyPrincipalAndInterest = BigDecimal.ZERO;
        }

        BigDecimal maxLoanAmount = MortgageMath.maxLoanAmount(
                maxMonthlyPrincipalAndInterest, request.annualInterestRate(), request.loanTermYears());
        BigDecimal maxHomePrice = maxLoanAmount.add(request.downPayment());

        AffordabilityAssumptions assumptions = new AffordabilityAssumptions(
                FRONT_END_RATIO_PERCENT,
                BACK_END_RATIO_PERCENT,
                request.annualInterestRate(),
                request.loanTermYears(),
                RATE_SOURCE
        );

        return new AffordabilityCalculationResponse(
                toMoney(monthlyGrossIncome),
                toMoney(maxMonthlyHousingPayment),
                toMoney(maxMonthlyPrincipalAndInterest),
                toMoney(maxLoanAmount),
                toMoney(maxHomePrice),
                assumptions,
                notes,
                DISCLAIMER
        );
    }

    private static BigDecimal percentOf(BigDecimal value, BigDecimal percent) {
        return value.multiply(percent).divide(HUNDRED, MC);
    }

    private static BigDecimal toMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
