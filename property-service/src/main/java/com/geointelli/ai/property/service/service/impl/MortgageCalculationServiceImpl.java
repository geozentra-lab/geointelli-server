package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;
import com.geointelli.ai.property.service.dto.MortgageCalculationResponse;
import com.geointelli.ai.property.service.service.MortgageCalculationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Service
public class MortgageCalculationServiceImpl implements MortgageCalculationService {


    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public MortgageCalculationResponse calculateMortgage(MortgageCalculationRequest request) {

        if (request.downPayment().compareTo(request.price()) > 0) {
            throw new IllegalArgumentException("downPayment must not exceed price");
        }

        BigDecimal monthlyPayment;

        BigDecimal loanAmount = toMoney(request.price().subtract(request.downPayment()));
        int numberOfPayments = request.loanTermYears() * 12;

        if (request.annualInterestRate().compareTo(BigDecimal.ZERO) == 0) {
            // If interest rate is 0, the monthly payment is simply the loan amount divided by number of payments
            monthlyPayment = loanAmount.divide(BigDecimal.valueOf(numberOfPayments), MC);
            monthlyPayment = toMoney(monthlyPayment);
        } else {
            // Convert annual interest rate to monthly and from percentage to decimal
            BigDecimal monthlyInterestRate = request.annualInterestRate().divide(HUNDRED, MC).divide(TWELVE, MC);

            // Amortized payment: P * r * (1+r)^n / ((1+r)^n - 1)
            BigDecimal onePlusRPowerN = BigDecimal.ONE.add(monthlyInterestRate).pow(numberOfPayments, MC);
            monthlyPayment = loanAmount.multiply(monthlyInterestRate)
                    .multiply(onePlusRPowerN)
                    .divide(onePlusRPowerN.subtract(BigDecimal.ONE), MC);

            monthlyPayment = toMoney(monthlyPayment);
        }

        BigDecimal monthlyPropertyTax = monthly(request.annualPropertyTax());
        BigDecimal monthlyInsurance = monthly(request.annualHomeInsurance());
        BigDecimal monthlyHoaFees = toMoney(request.monthlyHoaFees());

        BigDecimal totalMonthlyKnownCosts = monthlyPayment
                .add(monthlyPropertyTax == null ? BigDecimal.ZERO : monthlyPropertyTax)
                .add(monthlyInsurance == null ? BigDecimal.ZERO : monthlyInsurance)
                .add(monthlyHoaFees == null ? BigDecimal.ZERO : monthlyHoaFees);

        return new MortgageCalculationResponse(
                loanAmount,
                monthlyPayment,
                monthlyPropertyTax,
                monthlyInsurance,
                monthlyHoaFees,
                totalMonthlyKnownCosts
        );
    }

    private static BigDecimal monthly(BigDecimal annual) {
        if (annual == null) {
            return null;
        }
        return toMoney(annual.divide(TWELVE, MC));
    }

    private static BigDecimal toMoney(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
