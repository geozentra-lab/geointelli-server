package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.AdminCoverageHealthDTO;
import com.geointelli.ai.property.service.dto.AdminCoverageHealthDTO.Coverage;
import com.geointelli.ai.property.service.dto.AdminCoverageHealthDTO.ProviderHealth;
import com.geointelli.ai.property.service.dto.AdminCoverageHealthDTO.Quality;
import com.geointelli.ai.property.service.dto.AdminCoverageHealthDTO.SourceCoverage;
import com.geointelli.ai.property.service.dto.DataQualityReportDTO;
import com.geointelli.ai.property.service.dto.DataQualityRuleSummaryDTO;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionJobStatus;
import com.geointelli.ai.property.service.repository.CoverageHealthReader;
import com.geointelli.ai.property.service.repository.CoverageHealthReader.CoverageCounts;
import com.geointelli.ai.property.service.repository.CoverageHealthReader.ProviderJobStats;
import com.geointelli.ai.property.service.service.AdminCoverageHealthService;
import com.geointelli.ai.property.service.service.DataQualityService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Builds the dashboard without making the caller wait for the expensive counts. The data-quality scan (SCRUM-189)
 * and the coverage counts read whole tables (about two minutes on 580,000 properties). The quality block is taken on
 * each request from the counts the data-quality service keeps in memory and recalculates in the background
 * (SCRUM-206), so it never waits for a recount. The coverage counts are kept here as one snapshot with its time and
 * recalculated on a background thread once it is older than {@code admin.coverage-health.refresh-seconds}. A request
 * returns what it finds at once; before the first count exists, quality and coverage are UNAVAILABLE with the reason
 * that they are being calculated. Provider health reads a few rows per provider and is always read live. Nothing
 * runs while nobody asks for the dashboard, and only one coverage refresh runs at a time.
 */
@Service
@Slf4j
public class AdminCoverageHealthServiceImpl implements AdminCoverageHealthService {

    static final String MIAMI_DADE_API = "MIAMI_DADE_API";
    static final String CSV_IMPORT = "CSV_IMPORT";
    static final String MAPILLARY = "MAPILLARY";
    static final String CALCULATING = "Being calculated in the background; try again in a few minutes";
    private static final BigDecimal SMALLEST_SHARE = new BigDecimal("0.01");
    private static final BigDecimal LARGEST_PARTIAL_SHARE = new BigDecimal("99.99");
    private static final BigDecimal ALL = new BigDecimal("100.00");

    private final DataQualityService dataQualityService;
    private final CoverageHealthReader coverageHealthReader;
    private final Duration refreshAfter;
    private final Clock clock;
    private final Executor refresher;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    /** Last coverage block with the time it was read; null until the first refresh ends. */
    private volatile Snapshot snapshot;

    /** Coverage as read at {@code takenAt}. */
    record Snapshot(Coverage coverage, Instant takenAt) {
    }

    @Autowired
    public AdminCoverageHealthServiceImpl(DataQualityService dataQualityService,
                                          CoverageHealthReader coverageHealthReader,
                                          @Value("${admin.coverage-health.refresh-seconds:600}") long refreshSeconds) {
        this(dataQualityService, coverageHealthReader, refreshSeconds, Clock.systemUTC(), backgroundThread());
    }

    AdminCoverageHealthServiceImpl(DataQualityService dataQualityService, CoverageHealthReader coverageHealthReader,
                                   long refreshSeconds, Clock clock, Executor refresher) {
        if (refreshSeconds < 1) {
            throw new IllegalArgumentException("admin.coverage-health.refresh-seconds must be at least 1");
        }
        this.dataQualityService = dataQualityService;
        this.coverageHealthReader = coverageHealthReader;
        this.refreshAfter = Duration.ofSeconds(refreshSeconds);
        this.clock = clock;
        this.refresher = refresher;
    }

    private static ExecutorService backgroundThread() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "coverage-health-refresh");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void stop() {
        if (refresher instanceof ExecutorService executor) {
            executor.shutdownNow();
        }
    }

    @Override
    public AdminCoverageHealthDTO getDashboard() {
        Instant observedAt = clock.instant();
        Snapshot current = snapshot;
        if (current == null || !current.takenAt().plus(refreshAfter).isAfter(observedAt)) {
            refreshInBackground();
            // a refresh that ran on the calling thread (tests) is already visible; a background one is not yet
            current = snapshot;
        }
        Quality quality = quality();
        Coverage coverage = current == null
                ? new Coverage(AdminCoverageHealthDTO.UNAVAILABLE, null, null, List.of(), CALCULATING)
                : current.coverage();
        return new AdminCoverageHealthDTO(observedAt, quality, coverage, providers(), AdminCoverageHealthDTO.NOTE);
    }

    /** Starts one refresh unless one is already running. */
    private void refreshInBackground() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            refresher.execute(() -> {
                try {
                    refresh();
                } catch (RuntimeException e) {
                    log.warn("Coverage could not be refreshed ({})", e.getClass().getSimpleName());
                } finally {
                    refreshing.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            refreshing.set(false);
        }
    }

    /**
     * Reads coverage now. When its records cannot be read, it keeps its last good value, with its older time, rather
     * than replacing it with an error; without a good value it is UNAVAILABLE.
     */
    void refresh() {
        Snapshot previous = snapshot;
        Instant takenAt = clock.instant();
        Coverage coverage = coverage(takenAt);
        if (coverage == null) {
            coverage = previous != null && AdminCoverageHealthDTO.AVAILABLE.equals(previous.coverage().status())
                    ? previous.coverage()
                    : new Coverage(AdminCoverageHealthDTO.UNAVAILABLE, null, null, List.of(),
                            "Coverage counts could not be read");
        }
        snapshot = new Snapshot(coverage, takenAt);
    }

    /**
     * Rule counts of the data-quality scan, the summary only, as the data-quality service keeps them; it answers at
     * once and recounts in the background (SCRUM-206). UNAVAILABLE before the first count ends or when the report
     * cannot be read. The total is null when a rule has no count, never a partial sum.
     */
    private Quality quality() {
        DataQualityReportDTO report;
        try {
            report = dataQualityService.getReport(null, 0, 1);
        } catch (DataAccessException | TransactionException e) {
            return new Quality(AdminCoverageHealthDTO.UNAVAILABLE, null, null, List.of(),
                    "Data-quality findings could not be read");
        }
        if (report.getGeneratedAt() == null) {
            return new Quality(AdminCoverageHealthDTO.UNAVAILABLE, null, null, List.of(), CALCULATING);
        }
        Long totalIssues = 0L;
        for (DataQualityRuleSummaryDTO rule : report.getSummary()) {
            if (rule.getIssueCount() == null) {
                totalIssues = null;
                break;
            }
            totalIssues += rule.getIssueCount();
        }
        return new Quality(AdminCoverageHealthDTO.AVAILABLE, report.getGeneratedAt(), totalIssues,
                report.getSummary(), null);
    }

    /** Properties covered by each source, counted at {@code countedAt}; null when the counts cannot be read. */
    private Coverage coverage(Instant countedAt) {
        CoverageCounts counts;
        try {
            counts = coverageHealthReader.countCoverage();
        } catch (DataAccessException e) {
            return null;
        }
        long total = counts.totalProperties();
        if (total == 0) {
            return new Coverage(AdminCoverageHealthDTO.UNAVAILABLE, countedAt, 0L, List.of(), "No property is recorded");
        }
        List<SourceCoverage> sources = List.of(
                source("ADDRESSES", counts.addresses(), total),
                source("SALES", counts.sales(), total),
                source("ASSESSMENTS", counts.assessments(), total),
                source("TAXES", counts.taxes(), total),
                source("IMAGES", counts.images(), total),
                source("VALUATIONS", counts.valuations(), total));
        return new Coverage(AdminCoverageHealthDTO.AVAILABLE, countedAt, total, sources, null);
    }

    /**
     * Share of properties with the source, two decimals. Rounding never claims full or no coverage that is not
     * there: 100.00 only when every property is covered (else at most 99.99), 0.00 only when none is (else at
     * least 0.01).
     */
    static SourceCoverage source(String name, long covered, long total) {
        BigDecimal percent = BigDecimal.valueOf(covered).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        if (covered < total && percent.compareTo(ALL) >= 0) {
            percent = LARGEST_PARTIAL_SHARE;
        } else if (covered > 0 && percent.signum() == 0) {
            percent = SMALLEST_SHARE;
        }
        return new SourceCoverage(name, covered, percent);
    }

    /** One entry per supported provider, from its recorded jobs; read live, it is a few rows. */
    private List<ProviderHealth> providers() {
        return List.of(
                provider(MIAMI_DADE_API, () -> coverageHealthReader.ingestionJobStats(ImportJobType.PROPERTY_INGESTION)),
                provider(CSV_IMPORT, () -> coverageHealthReader.ingestionJobStats(ImportJobType.CSV_IMPORT)),
                provider(MAPILLARY, coverageHealthReader::imageBackfillJobStats));
    }

    /** HEALTHY if the latest finished job completed, DEGRADED otherwise, UNAVAILABLE without a finished job. */
    private static ProviderHealth provider(String name, Supplier<ProviderJobStats> read) {
        ProviderJobStats stats;
        try {
            stats = read.get();
        } catch (DataAccessException e) {
            return new ProviderHealth(name, AdminCoverageHealthDTO.UNAVAILABLE, null, null, null, null, 0,
                    "Job records could not be read");
        }
        if (stats.lastJobStatus() == null) {
            String reason = stats.activeJobs() > 0 ? "The first job is still queued or running"
                    : "No finished job is recorded";
            return new ProviderHealth(name, AdminCoverageHealthDTO.UNAVAILABLE, null, null, stats.lastSuccessAt(),
                    null, stats.activeJobs(), reason);
        }
        String status = IngestionJobStatus.COMPLETED.equals(stats.lastJobStatus())
                ? AdminCoverageHealthDTO.HEALTHY : AdminCoverageHealthDTO.DEGRADED;
        return new ProviderHealth(name, status, stats.lastJobStatus(), stats.lastJobAt(), stats.lastSuccessAt(),
                stats.lastErrorCode(), stats.activeJobs(), null);
    }
}
