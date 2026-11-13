package com.geointelli.ai.property.service.util;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MortgageMathTest {

    private static final BigDecimal LOAN = BigDecimal.valueOf(240000);
    private static final BigDecimal RATE = BigDecimal.valueOf(6);

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    @Nested
    @DisplayName("RemainingBalance Tests")
    class RemainingBalance {

        @Test
        @DisplayName("When no payment is made, then the whole loan is still owed")
        void whenNoPaymentMade_thenReturnLoanAmount() {
            assertEquals(new BigDecimal("240000.00"), money(MortgageMath.remainingBalance(LOAN, RATE, 30, 0)));
        }

        @Test
        @DisplayName("When payments are made, then return the amortized balance")
        void whenPaymentsMade_thenReturnAmortizedBalance() {
            assertEquals(new BigDecimal("237052.77"), money(MortgageMath.remainingBalance(LOAN, RATE, 30, 12)));
            assertEquals(new BigDecimal("223330.46"), money(MortgageMath.remainingBalance(LOAN, RATE, 30, 60)));
            assertEquals(new BigDecimal("200845.74"), money(MortgageMath.remainingBalance(LOAN, RATE, 30, 120)));
        }

        @Test
        @DisplayName("When the balance is rebuilt month by month, then it matches the formula")
        void whenRebuiltMonthByMonth_thenMatchFormula() {
            // Arrange
            BigDecimal monthlyRate = new BigDecimal("0.005");
            BigDecimal payment = MortgageMath.monthlyPayment(LOAN, RATE, 30);
            BigDecimal balance = LOAN;

            // Act
            for (int month = 0; month < 60; month++) {
                balance = balance.add(balance.multiply(monthlyRate)).subtract(payment);
            }

            // Assert
            assertEquals(money(balance), money(MortgageMath.remainingBalance(LOAN, RATE, 30, 60)));
        }

        @Test
        @DisplayName("When the rate is 0, then each payment repays the same share")
        void whenRateIsZero_thenRepayLinearly() {
            assertEquals(new BigDecimal("120000.00"),
                    money(MortgageMath.remainingBalance(LOAN, BigDecimal.ZERO, 10, 60)));
        }

        @Test
        @DisplayName("When every payment is made, or more, then nothing is owed")
        void whenLoanIsRepaid_thenReturnZero() {
            assertEquals(0, MortgageMath.remainingBalance(LOAN, RATE, 30, 360).signum());
            assertEquals(0, MortgageMath.remainingBalance(LOAN, RATE, 30, 480).signum());
        }

        @Test
        @DisplayName("When monthsPaid is negative, then throw IllegalArgumentException")
        void whenMonthsPaidIsNegative_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> MortgageMath.remainingBalance(LOAN, RATE, 30, -1));
        }
    }
}
