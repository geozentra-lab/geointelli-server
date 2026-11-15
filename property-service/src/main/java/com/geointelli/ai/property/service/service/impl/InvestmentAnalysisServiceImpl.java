package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.*;
import com.geointelli.ai.property.service.service.InvestmentAnalysisService;
import com.geointelli.ai.property.service.service.InvestmentFinancingService;
import com.geointelli.ai.property.service.service.InvestmentOperatingService;
import com.geointelli.ai.property.service.service.InvestmentProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Composes operating → financing → projection; reuses the existing services without changing their results. */
@Service
@RequiredArgsConstructor
public class InvestmentAnalysisServiceImpl implements InvestmentAnalysisService {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Used when no financing section is sent: every financing input is missing, nothing is assumed. */
    private static final InvestmentFinancingRequest NO_FINANCING =
            new InvestmentFinancingRequest(null, null, null, null, null);

    private final InvestmentOperatingService investmentOperatingService;
    private final InvestmentFinancingService investmentFinancingService;
    private final InvestmentProjectionService investmentProjectionService;


    @Override
    public InvestmentAnalysisResponse analyze(InvestmentAnalysisRequest request) {
        // The rent is never filled in: without it there is nothing to analyze
        if (request == null || request.operating() == null || request.operating().monthlyRent() == null) {
            throw new IllegalArgumentException("operating.monthlyRent is required");
        }
        List<String> notes = new ArrayList<>();

        // 1. Current, unfinanced (NOI)
        InvestmentOperatingResult operating = investmentOperatingService.calculateOperatingCashFlow(request.operating());

        // 2. Current, financed (debt service, cash flow, cap rate, cash-on-cash)
        InvestmentFinancingRequest financingRequest = request.financing() == null ? NO_FINANCING : request.financing();
        if (request.financing() == null) {
            notes.add("No financing section: financed metrics unavailable.");
        }
        InvestmentFinancingResult financing =
                investmentFinancingService.calculateFinancingMetrics(financingRequest, operating);

        // Gross yield: annual gross rent / purchase price × 100 (zero denominator guarded)
        BigDecimal grossYieldPercent = null;
        BigDecimal purchasePrice = financingRequest.purchasePrice();
        if (purchasePrice == null || purchasePrice.signum() == 0) {
            notes.add("Purchase price missing: gross yield unavailable.");
        } else {
            grossYieldPercent = operating.annualGrossRent().multiply(HUNDRED)
                    .divide(purchasePrice, MC)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // 3. Future, labelled separately and only when asked for
        InvestmentProjectionResult projection = null;
        if (request.projection() == null) {
            notes.add("No projection requested: equity and appreciation not projected.");
        } else {
            projection = investmentProjectionService.project(request.projection(), financingRequest, financing);
        }

        return new InvestmentAnalysisResponse(operating, financing, grossYieldPercent, projection, notes);
    }
}
