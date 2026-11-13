package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.InvestmentAnalysisRequest;
import com.geointelli.ai.property.service.dto.InvestmentAnalysisResponse;

/**
 * Analyzes a residential investment scenario (SCRUM-64) by composing the operating, financing and projection
 * services. Pure calculation: never reads or updates property records.
 */
public interface InvestmentAnalysisService {

    /**
     * Calculates the unfinanced operating result, the financed metrics, the gross yield and, when requested,
     * the equity projection. Missing inputs are never filled in: the values that need them are null with a note.
     *
     * @param request operating inputs (monthly rent required), optional financing and optional projection
     * @return current returns (operating and financing), gross yield, labelled projection and notes
     * @throws IllegalArgumentException if the operating section or the monthly rent is missing, or a supplied
     *         input is out of range
     */
    InvestmentAnalysisResponse analyze(InvestmentAnalysisRequest request);
}
