package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestmentFinancingRequest;
import com.geointelli.ai.property.service.dto.InvestmentFinancingResult;
import com.geointelli.ai.property.service.dto.InvestmentProjectionRequest;
import com.geointelli.ai.property.service.dto.InvestmentProjectionResult;
import com.geointelli.ai.property.service.service.InvestmentProjectionService;
import com.geointelli.ai.property.service.util.MortgageMath;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Equity projection built on the financed result; appreciation is used only when the user supplies a rate. */
@Service
public class InvestmentProjectionServiceImpl implements InvestmentProjectionService {


    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    static final int MAX_HORIZON_YEARS = 30;
    static final BigDecimal MAX_RATE_PERCENT = BigDecimal.valueOf(50);

    @Override
    public InvestmentProjectionResult project(InvestmentProjectionRequest request, InvestmentFinancingRequest financing, InvestmentFinancingResult financingResult) {
        // The controller validates the body; checked again so the service is safe to reuse on its own
        if (request == null || request.horizonYears() == null
                || request.horizonYears() < 1 || request.horizonYears() > MAX_HORIZON_YEARS) {
            throw new IllegalArgumentException("horizonYears must be between 1 and " + MAX_HORIZON_YEARS);
        }
        BigDecimal rate = request.annualAppreciationRatePercent();
        if (rate != null && rate.abs().compareTo(MAX_RATE_PERCENT) > 0) {
            throw new IllegalArgumentException("annualAppreciationRatePercent must be between -50 and 50");
        }

        int years = request.horizonYears();
        BigDecimal purchasePrice = financing == null ? null : financing.purchasePrice();
        BigDecimal downPayment = financing == null ? null : financing.downPayment();
        List<String> assumptions = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        assumptions.add("Rent, vacancy and expenses stay at their current level for " + years + " years (no growth).");

        // Equity from the down payment
        if (downPayment == null) {
            notes.add("Down payment not provided: equity from the down payment unavailable.");
        }

        // Equity from the loan principal repaid, with the same amortization as the debt service
        BigDecimal remainingLoanBalance = null;
        BigDecimal equityFromLoanPaydown = null;
        BigDecimal loanAmount = financingResult == null ? null : financingResult.loanAmount();
        if (loanAmount == null) {
            notes.add("Financing inputs missing (purchase price, down payment, rate or term): "
                    + "loan balance and principal repaid unavailable.");
        } else {
            remainingLoanBalance = toMoney(MortgageMath.remainingBalance(loanAmount, financing.annualInterestRate(),
                    financing.loanTermYears(), years * 12));
            equityFromLoanPaydown = loanAmount.subtract(remainingLoanBalance);
            assumptions.add("Every scheduled loan payment is made for " + (years * 12) + " months.");
        }
        // Appreciation: only from a rate supplied by the user, never a market assumption
        BigDecimal projectedPropertyValue = null;
        BigDecimal equityFromAppreciation = null;
        if (rate == null) {
            notes.add("Appreciation rate not provided: property value not projected; no market appreciation is assumed.");
        } else if (purchasePrice == null) {
            notes.add("Purchase price missing: property value not projected.");
        } else {
            BigDecimal growth = BigDecimal.ONE.add(rate.divide(HUNDRED, MC)).pow(years, MC);
            projectedPropertyValue = toMoney(purchasePrice.multiply(growth, MC));
            equityFromAppreciation = projectedPropertyValue.subtract(purchasePrice);
            assumptions.add("Property value changes by " + rate.stripTrailingZeros().toPlainString()
                    + "% per year, as supplied by the user; this is not a market forecast.");
        }
        // Total equity from the parts that are known
        BigDecimal totalEquity = null;
        if (downPayment != null && equityFromLoanPaydown != null) {
            totalEquity = downPayment.add(equityFromLoanPaydown);
            if (equityFromAppreciation != null) {
                totalEquity = totalEquity.add(equityFromAppreciation);
            } else {
                notes.add("Total equity excludes appreciation (property value not projected).");
            }
        } else {
            notes.add("Total equity unavailable: down payment or loan details missing.");
        }

        // Cumulative financed cash flow, held constant
        BigDecimal cumulativeCashFlow = null;
        BigDecimal annualCashFlow = financingResult == null ? null : financingResult.annualCashFlow();
        if (annualCashFlow == null) {
            notes.add("Annual cash flow unavailable: cumulative cash flow not projected.");
        } else {
            cumulativeCashFlow = annualCashFlow.multiply(BigDecimal.valueOf(years));
        }

        return new InvestmentProjectionResult(
                years,
                rate,
                toMoney(projectedPropertyValue),
                toMoney(remainingLoanBalance),
                toMoney(downPayment),
                toMoney(equityFromLoanPaydown),
                toMoney(equityFromAppreciation),
                toMoney(totalEquity),
                toMoney(cumulativeCashFlow),
                assumptions,
                notes
        );
    }

    private static BigDecimal toMoney(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
