package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.InvestmentOperatingRequest;
import com.geointelli.ai.property.service.dto.InvestmentOperatingResult;

/**
 * Calculates the operating (unfinanced) cash flow of a rental property.
 * Pure calculation: never reads or updates property records.
 */
public interface InvestmentOperatingService {
    /**
     * Calculates rent, vacancy, operating expenses and net operating income (NOI), monthly and annual.
     * Vacancy is a percentage of gross rent; maintenance and management are percentages of effective income
     * (rent after vacancy). Missing rates or costs are never assumed: they are returned as null,
     * excluded from the totals and listed in the notes.
     *
     * @param request monthly rent (required), optional vacancy/maintenance/management rates (%),
     *                yearly property tax and insurance, monthly HOA and other expenses
     * @return monthly and annual income, expenses and NOI, the expense breakdown and notes on missing input
     */
    InvestmentOperatingResult calculateOperatingCashFlow(InvestmentOperatingRequest request);
}
