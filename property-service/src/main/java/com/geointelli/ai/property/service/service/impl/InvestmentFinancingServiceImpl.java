package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.*;
import com.geointelli.ai.property.service.service.InvestmentFinancingService;
import com.geointelli.ai.property.service.service.MortgageCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Financed metrics on top of the unfinanced operating result. Debt service comes from the
 * mortgage calculator without tax, insurance or HOA, which are already deducted in the NOI.
 */
@Service
@RequiredArgsConstructor
public class InvestmentFinancingServiceImpl implements InvestmentFinancingService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MortgageCalculationService mortgageCalculationService;

    @Override
    public InvestmentFinancingResult calculateFinancingMetrics(InvestmentFinancingRequest request, InvestmentOperatingResult operating) {
        BigDecimal annualNoi = operating.annualNetOperatingIncome();

        List<String> notes = new ArrayList<>();

        // Loan and debt service, reusing the mortgage calculator
        BigDecimal purchasePrice = request.purchasePrice();
        BigDecimal downPayment = request.downPayment();

        BigDecimal loanAmount = null;
        BigDecimal monthlyDebtService = null;
        BigDecimal annualDebtService = null;

        if (purchasePrice == null || downPayment == null
                || request.annualInterestRate() == null || request.loanTermYears() == null) {
            notes.add("Financing inputs missing (purchase price, down payment, rate or term): "
                    + "debt service, cash flow and cash-on-cash unavailable.");
        } else {
            if (downPayment.compareTo(purchasePrice) > 0) {
                throw new IllegalArgumentException("downPayment must not exceed purchasePrice");
            }
            // Tax, insurance and HOA are already in the NOI: passing them again would count them twice
            MortgageCalculationResponse mortgage = mortgageCalculationService.calculateMortgage(
                    new MortgageCalculationRequest(purchasePrice, downPayment,
                            request.annualInterestRate(), request.loanTermYears(), null, null, null));

            loanAmount = mortgage.loanAmount();
            monthlyDebtService = mortgage.monthlyPrincipalAndInterest();
            annualDebtService = monthlyDebtService.multiply(TWELVE);
        }

        // Financed cash flow: NOI minus debt service; may be negative
        BigDecimal annualCashFlow = null;
        BigDecimal monthlyCashFlow = null;
        if (annualDebtService != null) {
            annualCashFlow = annualNoi.subtract(annualDebtService);
            monthlyCashFlow = annualCashFlow.divide(TWELVE, MC);
        }

        // Cap rate: unfinanced, denominator = purchase price
        BigDecimal capRatePercent = null;
        if (purchasePrice == null) {
            notes.add("Purchase price missing: cap rate unavailable.");
        } else {
            capRatePercent = annualNoi.multiply(HUNDRED).divide(purchasePrice, MC);
        }
        // Cash-on-cash: financed, denominator = cash invested (down payment + closing costs)
        BigDecimal cashInvested = null;
        BigDecimal cashOnCashPercent = null;
        if (downPayment != null) {
            if (request.closingCosts() == null) {
                notes.add("Closing costs not provided: cash invested may be understated.");
            }
            cashInvested = downPayment.add(orZero(request.closingCosts()));

            if (cashInvested.signum() == 0) {
                notes.add("No cash invested: cash-on-cash unavailable.");
            } else if (annualCashFlow != null) {
                cashOnCashPercent = annualCashFlow.multiply(HUNDRED).divide(cashInvested, MC);
            }
        }

        return new InvestmentFinancingResult(
                toMoney(loanAmount),
                toMoney(monthlyDebtService),
                toMoney(annualDebtService),
                toMoney(monthlyCashFlow),
                toMoney(annualCashFlow),
                toMoney(capRatePercent),
                toMoney(cashInvested),
                toMoney(cashOnCashPercent),
                notes
        );
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal toMoney(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
