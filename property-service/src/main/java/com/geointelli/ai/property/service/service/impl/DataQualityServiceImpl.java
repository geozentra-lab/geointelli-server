package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.DataQualityIssueDTO;
import com.geointelli.ai.property.service.dto.DataQualityReportDTO;
import com.geointelli.ai.property.service.dto.DataQualityRuleSummaryDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import com.geointelli.ai.property.service.repository.DataQualityIssueRow;
import com.geointelli.ai.property.service.repository.DataQualityRepository;
import com.geointelli.ai.property.service.service.DataQualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Computes the data-quality report from read-only SQL rules; issues are recomputed on each request. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataQualityServiceImpl implements DataQualityService {

    static final int MAX_PAGE_SIZE = 100;
    static final String NOTE = "Issues are computed on each request; nothing is changed or deleted.";

    private final DataQualityRepository dataQualityRepository;

    @Override
    public DataQualityReportDTO getReport(DataQualityRule rule, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        DataQualityReportDTO report = new DataQualityReportDTO();
        report.setGeneratedAt(Instant.now());
        report.setNote(NOTE);

        List<DataQualityRuleSummaryDTO> summary = new ArrayList<>();
        long selectedRuleCount = 0;
        for (DataQualityRule current : DataQualityRule.values()) {
            DataQualityRuleSummaryDTO ruleSummary = toSummary(current, count(current));
            summary.add(ruleSummary);
            if (current == rule) {
                selectedRuleCount = ruleSummary.getIssueCount();
            }
        }
        report.setSummary(summary);

        if (rule != null) {
            List<DataQualityIssueDTO> issues = find(rule, size, (long) page * size).stream()
                    .map(row -> toIssue(rule, row))
                    .toList();
            long totalPages = (selectedRuleCount + size - 1) / size;
            report.setRule(rule);
            report.setIssues(new PageResponse<>(issues, page, size, selectedRuleCount, totalPages,
                    page == 0, page + 1 >= totalPages));
        }
        return report;
    }

    private long count(DataQualityRule rule) {
        return switch (rule) {
            case PROPERTY_MISSING_ADDRESS -> dataQualityRepository.countMissingAddress();
            case PROPERTY_MISSING_PARCEL -> dataQualityRepository.countMissingParcel();
            case ASSESSMENT_STALE -> dataQualityRepository.countStaleAssessment();
            case LOT_SIZE_CONFLICT -> dataQualityRepository.countLotSizeConflict();
            case SALE_PRICE_OUTLIER -> dataQualityRepository.countSalePriceOutlier();
            case SALE_DUPLICATE -> dataQualityRepository.countSaleDuplicate();
            case ASSESSMENT_DUPLICATE -> dataQualityRepository.countAssessmentDuplicate();
        };
    }

    private List<DataQualityIssueRow> find(DataQualityRule rule, int limit, long offset) {
        return switch (rule) {
            case PROPERTY_MISSING_ADDRESS -> dataQualityRepository.findMissingAddress(limit, offset);
            case PROPERTY_MISSING_PARCEL -> dataQualityRepository.findMissingParcel(limit, offset);
            case ASSESSMENT_STALE -> dataQualityRepository.findStaleAssessment(limit, offset);
            case LOT_SIZE_CONFLICT -> dataQualityRepository.findLotSizeConflict(limit, offset);
            case SALE_PRICE_OUTLIER -> dataQualityRepository.findSalePriceOutlier(limit, offset);
            case SALE_DUPLICATE -> dataQualityRepository.findSaleDuplicate(limit, offset);
            case ASSESSMENT_DUPLICATE -> dataQualityRepository.findAssessmentDuplicate(limit, offset);
        };
    }

    private DataQualityRuleSummaryDTO toSummary(DataQualityRule rule, long issueCount) {
        DataQualityRuleSummaryDTO summary = new DataQualityRuleSummaryDTO();
        summary.setRule(rule);
        summary.setCategory(rule.getCategory());
        summary.setEntityType(rule.getEntityType());
        summary.setSource(rule.getSource());
        summary.setDescription(rule.getDescription());
        summary.setIssueCount(issueCount);
        return summary;
    }

    private DataQualityIssueDTO toIssue(DataQualityRule rule, DataQualityIssueRow row) {
        DataQualityIssueDTO issue = new DataQualityIssueDTO();
        issue.setReference(rule.name() + ":" + rule.getEntityType() + ":" + row.getEntityId());
        issue.setRule(rule);
        issue.setCategory(rule.getCategory());
        issue.setEntityType(rule.getEntityType());
        issue.setEntityId(row.getEntityId());
        issue.setPropertyId(row.getPropertyId());
        issue.setFolio(row.getFolio());
        issue.setDetail(row.getDetail());
        issue.setSourceCounty(row.getSourceCounty());
        issue.setSourceState(row.getSourceState());
        issue.setSourcePropertyId(row.getSourcePropertyId());
        return issue;
    }
}
