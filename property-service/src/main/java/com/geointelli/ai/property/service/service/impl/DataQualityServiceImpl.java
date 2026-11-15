package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.DataQualityIssueDTO;
import com.geointelli.ai.property.service.dto.DataQualityReportDTO;
import com.geointelli.ai.property.service.dto.DataQualityRuleSummaryDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import com.geointelli.ai.property.service.repository.DataQualityIssueRow;
import com.geointelli.ai.property.service.repository.DataQualityRepository;
import com.geointelli.ai.property.service.service.DataQualityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the data-quality report from read-only SQL rules. Counting every rule scans the large tables, so the
 * counts are reused for {@code data-quality.summary-cache-seconds} and only one request at a time recalculates
 * them; the issue page of a rule is always read on request.
 */
@Service
@Transactional(readOnly = true)
public class DataQualityServiceImpl implements DataQualityService {

    static final int MAX_PAGE_SIZE = 100;

    private final DataQualityRepository dataQualityRepository;
    private final Duration countsReuse;
    private final Clock clock;
    private final String note;
    private final Object countsLock = new Object();
    private volatile RuleCounts cachedCounts;

    /** Issue count of every rule, calculated at {@code calculatedAt}. */
    private record RuleCounts(Instant calculatedAt, Map<DataQualityRule, Long> counts) {
    }

    /**
     * @param summaryCacheSeconds how long rule counts are reused; 0 recalculates them on each request
     */
    @Autowired
    public DataQualityServiceImpl(DataQualityRepository dataQualityRepository,
                                  @Value("${data-quality.summary-cache-seconds:600}") long summaryCacheSeconds) {
        this(dataQualityRepository, summaryCacheSeconds, Clock.systemUTC());
    }

    DataQualityServiceImpl(DataQualityRepository dataQualityRepository, long summaryCacheSeconds, Clock clock) {
        if (summaryCacheSeconds < 0) {
            throw new IllegalArgumentException("data-quality.summary-cache-seconds must be 0 or greater");
        }
        this.dataQualityRepository = dataQualityRepository;
        this.countsReuse = Duration.ofSeconds(summaryCacheSeconds);
        this.clock = clock;
        this.note = note(summaryCacheSeconds);
    }

    /** Explains to administrators how fresh the counts and the issue page are. */
    static String note(long summaryCacheSeconds) {
        String counts = summaryCacheSeconds == 0
                ? "Rule counts are calculated on each request"
                : "Rule counts were calculated at generatedAt and are reused for up to " + duration(summaryCacheSeconds);
        return counts + "; the issue page is read on each request. Nothing is changed or deleted.";
    }

    private static String duration(long seconds) {
        if (seconds % 60 != 0) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        long minutes = seconds / 60;
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    @Override
    public DataQualityReportDTO getReport(DataQualityRule rule, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        RuleCounts counts = counts();
        DataQualityReportDTO report = new DataQualityReportDTO();
        report.setGeneratedAt(counts.calculatedAt());
        report.setNote(note);
        report.setSummary(Arrays.stream(DataQualityRule.values())
                .map(current -> toSummary(current, counts.counts().get(current)))
                .toList());

        if (rule != null) {
            long selectedRuleCount = counts.counts().get(rule);
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

    /** Returns the reusable counts, or recalculates every rule once while other requests wait for the result. */
    private RuleCounts counts() {
        RuleCounts current = cachedCounts;
        if (isReusable(current)) {
            return current;
        }
        synchronized (countsLock) {
            current = cachedCounts;
            if (!isReusable(current)) {
                Instant calculatedAt = clock.instant();
                Map<DataQualityRule, Long> counts = new EnumMap<>(DataQualityRule.class);
                for (DataQualityRule rule : DataQualityRule.values()) {
                    counts.put(rule, count(rule));
                }
                current = new RuleCounts(calculatedAt, counts);
                cachedCounts = current;
            }
            return current;
        }
    }

    private boolean isReusable(RuleCounts counts) {
        return counts != null && clock.instant().isBefore(counts.calculatedAt().plus(countsReuse));
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
