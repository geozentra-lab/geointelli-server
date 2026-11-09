package com.geointelli.ai.property.service.util;

import java.math.BigDecimal;
import java.math.MathContext;

public final class MortgageMath {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MortgageMath() {
    }

    public static BigDecimal monthlyPayment(BigDecimal loanAmount, BigDecimal annualRatePercent, int loanTermYears) {

        BigDecimal monthlyPayment;

        int numberOfPayments = loanTermYears * 12;

        if (annualRatePercent.compareTo(BigDecimal.ZERO) == 0) {
            // If interest rate is 0, the monthly payment is simply the loan amount divided by number of payments
            monthlyPayment = loanAmount.divide(BigDecimal.valueOf(numberOfPayments), MC);
        } else {
            // Convert annual interest rate to monthly and from percentage to decimal
            BigDecimal monthlyInterestRate = annualRatePercent.divide(HUNDRED, MC).divide(TWELVE, MC);

            // Amortized payment: P * r * (1+r)^n / ((1+r)^n - 1)
            BigDecimal onePlusRPowerN = BigDecimal.ONE.add(monthlyInterestRate).pow(numberOfPayments, MC);
            monthlyPayment = loanAmount.multiply(monthlyInterestRate)
                    .multiply(onePlusRPowerN)
                    .divide(onePlusRPowerN.subtract(BigDecimal.ONE), MC);
        }

        return monthlyPayment;
    }

    public static BigDecimal maxLoanAmount(BigDecimal monthlyPayment, BigDecimal annualRatePercent, int loanTermYears) {
        BigDecimal maxLoanAmount;

        int numberOfPayments = loanTermYears * 12;

        if (annualRatePercent.compareTo(BigDecimal.ZERO) == 0) {
            // If interest rate is 0, the max loan amount is simply the monthly payment multiplied by number of payments
            maxLoanAmount = monthlyPayment.multiply(BigDecimal.valueOf(numberOfPayments));
        } else {
            // Convert annual interest rate to monthly and from percentage to decimal
            BigDecimal monthlyInterestRate = annualRatePercent.divide(HUNDRED, MC).divide(TWELVE, MC);

            // Rearranged amortized payment formula to solve for P: P = M * ((1+r)^n - 1) / (r * (1+r)^n)
            BigDecimal onePlusRPowerN = BigDecimal.ONE.add(monthlyInterestRate).pow(numberOfPayments, MC);
            maxLoanAmount = monthlyPayment.multiply(onePlusRPowerN.subtract(BigDecimal.ONE))
                    .divide(monthlyInterestRate.multiply(onePlusRPowerN), MC);
        }

        return maxLoanAmount;

    }

    /**
     * Remaining loan balance after a number of monthly payments, using the same amortization as
     * {@link #monthlyPayment}. Returns 0 once the loan is fully repaid.
     *
     * @param loanAmount        amount borrowed
     * @param annualRatePercent annual interest rate in percent (0 allowed)
     * @param loanTermYears     loan term in years
     * @param monthsPaid        number of monthly payments already made, 0 or greater
     * @return the balance still owed, never negative
     * @throws IllegalArgumentException if monthsPaid is negative
     */
    public static BigDecimal remainingBalance(BigDecimal loanAmount, BigDecimal annualRatePercent,
                                              int loanTermYears, int monthsPaid) {
        if (monthsPaid < 0) {
            throw new IllegalArgumentException("monthsPaid must be 0 or greater");
        }
        int numberOfPayments = loanTermYears * 12;
        if (monthsPaid >= numberOfPayments) {
            return BigDecimal.ZERO;
        }
        BigDecimal monthlyPayment = monthlyPayment(loanAmount, annualRatePercent, loanTermYears);
        BigDecimal balance;
        if (annualRatePercent.compareTo(BigDecimal.ZERO) == 0) {
            // Without interest, each payment repays the same share of the loan
            balance = loanAmount.subtract(monthlyPayment.multiply(BigDecimal.valueOf(monthsPaid)), MC);
        } else {
            // Balance after k payments: P * (1+r)^k - M * ((1+r)^k - 1) / r
            BigDecimal monthlyInterestRate = annualRatePercent.divide(HUNDRED, MC).divide(TWELVE, MC);
            BigDecimal onePlusRPowerK = BigDecimal.ONE.add(monthlyInterestRate).pow(monthsPaid, MC);
            balance = loanAmount.multiply(onePlusRPowerK)
                    .subtract(monthlyPayment.multiply(onePlusRPowerK.subtract(BigDecimal.ONE))
                            .divide(monthlyInterestRate, MC), MC);
        }
        return balance.max(BigDecimal.ZERO);
    }
}
