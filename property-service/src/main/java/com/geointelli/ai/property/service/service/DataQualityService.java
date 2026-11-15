package com.geointelli.ai.property.service.service;


import com.geointelli.ai.property.service.dto.DataQualityReportDTO;
import com.geointelli.ai.property.service.enums.DataQualityRule;

/** Detects and summarizes data-quality issues for administrators (SCRUM-189). Read-only: nothing is changed or deleted. */
public interface DataQualityService {

    /**
     * Runs every data-quality rule and returns the number of issues per rule; when a rule is given, also returns
     * one page of its issues, ordered by entity ID, each with a reproducible reference and its provenance.
     *
     * @param rule rule whose issues are listed, or {@code null} for the summary only
     * @param page zero-based page number, 0 or greater
     * @param size page size, between 1 and 100
     * @return the summary of every rule, plus the requested page of issues
     * @throws IllegalArgumentException if page or size is out of range
     */
    DataQualityReportDTO getReport(DataQualityRule rule, int page, int size);
}
