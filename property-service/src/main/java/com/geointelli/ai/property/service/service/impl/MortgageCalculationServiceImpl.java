package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;
import com.geointelli.ai.property.service.dto.MortgageCalculationResponse;
import com.geointelli.ai.property.service.service.MortgageCalculationService;
import com.geointelli.ai.property.service.util.MortgageMath;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Service
public class MortgageCalculationServiceImpl implements MortgageCalculationService {


    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    @Override
    public MortgageCalculationResponse calculateMortgage(MortgageCalculationRequest request) {

        if (request.downPayment().compareTo(request.price()) > 0) {
            throw new IllegalArgumentException("downPayment must not exceed price");
        }

        BigDecimal loanAmount = toMoney(request.price().subtract(request.downPayment()));

        BigDecimal monthlyPayment =  toMoney(MortgageMath.monthlyPayment(loanAmount, request.annualInterestRate(), request.loanTermYears()));

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
