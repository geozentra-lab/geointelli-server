package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestmentOperatingRequest;
import com.geointelli.ai.property.service.dto.InvestmentOperatingResult;
import com.geointelli.ai.property.service.service.InvestmentOperatingService;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;


@Service
public class InvestmentOperatingServiceImpl implements InvestmentOperatingService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public InvestmentOperatingResult calculateOperatingCashFlow(InvestmentOperatingRequest request) {

        List<String> notes = new ArrayList<>();

        // Income: gross rent minus vacancy
        BigDecimal annualGrossRent = request.monthlyRent().multiply(TWELVE);

        BigDecimal annualVacancyLoss = null;

        if (request.vacancyRatePercent() == null) {
            notes.add("Vacancy rate not provided: no vacancy deducted, income may be overstated.");
        } else {
            annualVacancyLoss = percentOf(annualGrossRent, request.vacancyRatePercent());
        }
        BigDecimal annualEffectiveIncome = annualGrossRent.subtract(orZero(annualVacancyLoss));

        // Expenses: only supplied ones are counted; missing ones are reported, never assumed
        BigDecimal annualMaintenance = null;
        if (request.maintenanceRatePercent() == null) {
            notes.add("Maintenance rate not provided: not included in expenses, NOI may be overstated.");
        } else {
            annualMaintenance = percentOf(annualEffectiveIncome, request.maintenanceRatePercent());
        }

        BigDecimal annualManagement = null;
        if (request.managementRatePercent() == null) {
            notes.add("Management rate not provided: not included in expenses, NOI may be overstated.");
        } else {
            annualManagement = percentOf(annualEffectiveIncome, request.managementRatePercent());
        }

        BigDecimal annualPropertyTax = request.annualPropertyTax();
        if (annualPropertyTax == null) {
            notes.add("Property tax not provided: not included in expenses, NOI may be overstated.");
        }

        BigDecimal annualInsurance = request.annualHomeInsurance();
        if (annualInsurance == null) {
            notes.add("Home insurance not provided: not included in expenses, NOI may be overstated.");
        }

        BigDecimal annualHoaFees = null;
        if (request.monthlyHoaFees() == null) {
            notes.add("HOA fees not provided: not included in expenses, NOI may be overstated.");
        } else {
            annualHoaFees = request.monthlyHoaFees().multiply(TWELVE);
        }

        BigDecimal annualOtherExpenses = null;
        if (request.monthlyOtherExpenses() == null) {
            notes.add("Other expenses not provided: not included in expenses, NOI may be overstated.");
        } else {
            annualOtherExpenses = request.monthlyOtherExpenses().multiply(TWELVE);
        }

        BigDecimal annualOperatingExpenses = orZero(annualMaintenance)
                .add(orZero(annualManagement))
                .add(orZero(annualPropertyTax))
                .add(orZero(annualInsurance))
                .add(orZero(annualHoaFees))
                .add(orZero(annualOtherExpenses));

        // NOI may be negative: the property costs more than it earns
        BigDecimal annualNetOperatingIncome = annualEffectiveIncome.subtract(annualOperatingExpenses);

        return new InvestmentOperatingResult(
                toMoney(monthly(annualGrossRent)),
                toMoney(annualGrossRent),
                toMoney(monthly(annualVacancyLoss)),
                toMoney(annualVacancyLoss),
                toMoney(monthly(annualEffectiveIncome)),
                toMoney(annualEffectiveIncome),
                toMoney(annualMaintenance),
                toMoney(annualManagement),
                toMoney(annualPropertyTax),
                toMoney(annualInsurance),
                toMoney(annualHoaFees),
                toMoney(annualOtherExpenses),
                toMoney(monthly(annualOperatingExpenses)),
                toMoney(annualOperatingExpenses),
                toMoney(monthly(annualNetOperatingIncome)),
                toMoney(annualNetOperatingIncome),
                notes
        );
    }

    private static BigDecimal monthly(BigDecimal annual) {
        return annual == null ? null : annual.divide(TWELVE, MC);
    }

    private static BigDecimal percentOf(BigDecimal value, BigDecimal percent) {
        return value.multiply(percent).divide(HUNDRED, MC);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal toMoney(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
