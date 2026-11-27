package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.DataQualityIssueDTO;
import com.geointelli.ai.property.service.dto.DataQualityReportDTO;
import com.geointelli.ai.property.service.dto.DataQualityReviewEventDTO;
import com.geointelli.ai.property.service.dto.DataQualityRuleSummaryDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import com.geointelli.ai.property.service.repository.DataQualityIssueRow;
import com.geointelli.ai.property.service.repository.DataQualityRepository;
import com.geointelli.ai.property.service.service.DataQualityReviewService;
import com.geointelli.ai.property.service.service.DataQualityService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Computes the data-quality report from read-only SQL rules. Counting every rule scans the large tables, so the
 * counts are kept in memory and recalculated on a background thread, one recount at a time, once they are older than
 * {@code data-quality.summary-cache-seconds} (SCRUM-206). A request never waits for a recount: it gets the last
 * counts, or none before the first recount ends, so it never holds a database connection while counting runs
 * elsewhere. Each rule is counted in its own read-only transaction limited to {@code data-quality.count-timeout-seconds};
 * a rule that runs longer or fails is reported without a count and with the reason, and the other rules keep theirs.
 * The issue page of a rule is always read on request, within {@link #PAGE_TIMEOUT_SECONDS}. Each issue of a page
 * carries its current review state, read from the review log of SCRUM-190 in one query.
 */
@Service
@Slf4j
public class DataQualityServiceImpl implements DataQualityService {

    static final int MAX_PAGE_SIZE = 100;
    /** Time limit of one issue page, below CloudFront's 60 s origin timeout, as for the comparables search. */
    static final int PAGE_TIMEOUT_SECONDS = 30;
    static final String CALCULATING = "Being counted in the background; refresh in a few minutes";

    private final DataQualityRepository dataQualityRepository;
    private final DataQualityReviewService reviewService;
    private final TransactionTemplate countTransaction;
    private final Duration countsReuse;
    private final int countTimeoutSeconds;
    private final Clock clock;
    private final Executor counter;
    private final String note;
    private final AtomicBoolean counting = new AtomicBoolean();
    private volatile RuleCounts cachedCounts;

    /**
     * Issue count of every rule, started at {@code calculatedAt} and finished at {@code finishedAt}. A rule without a
     * count (null) could not be counted; {@code unavailable} gives the reason.
     */
    record RuleCounts(Instant calculatedAt, Instant finishedAt, Map<DataQualityRule, Long> counts,
                      Map<DataQualityRule, String> unavailable) {
    }

    /**
     * @param summaryCacheSeconds how long counts are reused after a recount finishes; 0 starts a recount on each
     *                            request, which still returns the last counts at once
     * @param countTimeoutSeconds time limit of the count of one rule
     */
    @Autowired
    public DataQualityServiceImpl(DataQualityRepository dataQualityRepository, DataQualityReviewService reviewService,
                                  PlatformTransactionManager transactionManager,
                                  @Value("${data-quality.summary-cache-seconds:600}") long summaryCacheSeconds,
                                  @Value("${data-quality.count-timeout-seconds:120}") int countTimeoutSeconds) {
        this(dataQualityRepository, reviewService, transactionManager, summaryCacheSeconds, countTimeoutSeconds,
                Clock.systemUTC(), backgroundThread());
    }

    DataQualityServiceImpl(DataQualityRepository dataQualityRepository, DataQualityReviewService reviewService,
                           PlatformTransactionManager transactionManager, long summaryCacheSeconds,
                           int countTimeoutSeconds, Clock clock, Executor counter) {
        if (summaryCacheSeconds < 0) {
            throw new IllegalArgumentException("data-quality.summary-cache-seconds must be 0 or greater");
        }
        if (countTimeoutSeconds < 1) {
            throw new IllegalArgumentException("data-quality.count-timeout-seconds must be at least 1");
        }
        this.dataQualityRepository = dataQualityRepository;
        this.reviewService = reviewService;
        this.countTransaction = new TransactionTemplate(transactionManager);
        this.countTransaction.setReadOnly(true);
        this.countTransaction.setTimeout(countTimeoutSeconds);
        this.countTransaction.setName("data-quality-count");
        this.countsReuse = Duration.ofSeconds(summaryCacheSeconds);
        this.countTimeoutSeconds = countTimeoutSeconds;
        this.clock = clock;
        this.counter = counter;
        this.note = note(summaryCacheSeconds);
    }

    private static ExecutorService backgroundThread() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "data-quality-count");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void stop() {
        if (counter instanceof ExecutorService executor) {
            executor.shutdownNow();
        }
    }

    /** Explains to administrators how fresh the counts and the issue page are. */
    static String note(long summaryCacheSeconds) {
        String counts = summaryCacheSeconds == 0
                ? "Rule counts are recalculated in the background on each request; generatedAt shows when the "
                        + "returned counts were calculated"
                : "Rule counts were calculated at generatedAt and are reused for up to " + duration(summaryCacheSeconds)
                        + ", then recalculated in the background";
        return counts + ". Before the first count, issueCount is null and calculating is true. The issue page is "
                + "read on each request. Nothing is changed or deleted.";
    }

    private static String duration(long seconds) {
        if (seconds % 60 != 0) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        long minutes = seconds / 60;
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    @Override
    @Transactional(readOnly = true, timeout = PAGE_TIMEOUT_SECONDS)
    public DataQualityReportDTO getReport(DataQualityRule rule, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        RuleCounts counts = currentCounts();
        DataQualityReportDTO report = new DataQualityReportDTO();
        report.setGeneratedAt(counts == null ? null : counts.calculatedAt());
        report.setCalculating(counting.get());
        report.setNote(note);
        report.setSummary(Arrays.stream(DataQualityRule.values())
                .map(current -> toSummary(current, counts))
                .toList());

        if (rule != null) {
            Long ruleCount = counts == null ? null : counts.counts().get(rule);
            long offset = (long) page * size;
            // without a count, one more row tells whether a next page exists
            List<DataQualityIssueRow> rows = find(rule, ruleCount == null ? size + 1 : size, offset);
            boolean more = ruleCount == null && rows.size() > size;
            List<DataQualityIssueDTO> issues = rows.stream()
                    .limit(size)
                    .map(row -> toIssue(rule, row))
                    .toList();
            // review state of the whole page in one query (SCRUM-190); issues never reviewed stay OPEN
            Map<String, DataQualityReviewEventDTO> reviews = reviewService.latestByReference(
                    issues.stream().map(DataQualityIssueDTO::getReference).toList());
            for (DataQualityIssueDTO issue : issues) {
                DataQualityReviewEventDTO latest = reviews.get(issue.getReference());
                if (latest != null) {
                    issue.setReviewStatus(latest.toStatus());
                    issue.setReviewedByUserId(latest.reviewedByUserId());
                    issue.setReviewedAt(latest.reviewedAt());
                    issue.setReviewReason(latest.reason());
                }
            }
            // without a count, the total is the lowest one the pages read so far prove
            long total = ruleCount != null ? ruleCount : offset + issues.size() + (more ? 1 : 0);
            long totalPages = (total + size - 1) / size;
            boolean last = ruleCount != null ? page + 1 >= totalPages : !more;
            report.setRule(rule);
            report.setIssues(new PageResponse<>(issues, page, size, total, totalPages, page == 0, last));
        }
        return report;
    }

    /** Returns the last counts at once, starting a background recount when they are missing or too old. */
    private RuleCounts currentCounts() {
        RuleCounts current = cachedCounts;
        if (!isReusable(current)) {
            countInBackground();
            // a recount that ran on the calling thread (tests) is already visible; a background one is not yet
            current = cachedCounts;
        }
        return current;
    }

    private boolean isReusable(RuleCounts counts) {
        return counts != null && clock.instant().isBefore(counts.finishedAt().plus(countsReuse));
    }

    /** Starts one recount unless one is already running. */
    private void countInBackground() {
        if (!counting.compareAndSet(false, true)) {
            return;
        }
        try {
            counter.execute(() -> {
                try {
                    cachedCounts = countEveryRule();
                } catch (RuntimeException e) {
                    log.warn("Data-quality rules could not be counted ({})", e.getClass().getSimpleName());
                } finally {
                    counting.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            counting.set(false);
        }
    }

    /** Counts every rule, each in its own time-limited transaction; a rule that fails keeps no count. */
    RuleCounts countEveryRule() {
        Instant calculatedAt = clock.instant();
        Map<DataQualityRule, Long> counts = new EnumMap<>(DataQualityRule.class);
        Map<DataQualityRule, String> unavailable = new EnumMap<>(DataQualityRule.class);
        for (DataQualityRule rule : DataQualityRule.values()) {
            try {
                counts.put(rule, countTransaction.execute(status -> count(rule)));
            } catch (QueryTimeoutException | TransactionTimedOutException e) {
                counts.put(rule, null);
                unavailable.put(rule, "Counting took longer than " + countTimeoutSeconds + " seconds and was stopped");
                log.warn("Data-quality rule {} was not counted within {} s", rule, countTimeoutSeconds);
            } catch (RuntimeException e) {
                counts.put(rule, null);
                unavailable.put(rule, "Could not be counted");
                log.warn("Data-quality rule {} could not be counted ({})", rule, e.getClass().getSimpleName());
            }
        }
        return new RuleCounts(calculatedAt, clock.instant(), Collections.unmodifiableMap(counts),
                Collections.unmodifiableMap(unavailable));
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

    private DataQualityRuleSummaryDTO toSummary(DataQualityRule rule, RuleCounts counts) {
        DataQualityRuleSummaryDTO summary = new DataQualityRuleSummaryDTO();
        summary.setRule(rule);
        summary.setCategory(rule.getCategory());
        summary.setEntityType(rule.getEntityType());
        summary.setSource(rule.getSource());
        summary.setDescription(rule.getDescription());
        summary.setIssueCount(counts == null ? null : counts.counts().get(rule));
        summary.setUnavailableReason(counts == null ? CALCULATING : counts.unavailable().get(rule));
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
