package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.AdminCoverageHealthDTO;
import com.geointelli.ai.property.service.dto.AdminCoverageHealthDTO.ProviderHealth;
import com.geointelli.ai.property.service.dto.DataQualityReportDTO;
import com.geointelli.ai.property.service.dto.DataQualityRuleSummaryDTO;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.repository.CoverageHealthReader;
import com.geointelli.ai.property.service.repository.CoverageHealthReader.CoverageCounts;
import com.geointelli.ai.property.service.repository.CoverageHealthReader.ProviderJobStats;
import com.geointelli.ai.property.service.service.DataQualityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AdminCoverageHealthServiceImplTest {

    DataQualityService dataQualityService = mock(DataQualityService.class);
    CoverageHealthReader coverageHealthReader = mock(CoverageHealthReader.class);
    MovingClock clock = new MovingClock(Instant.parse("2026-11-16T10:00:00Z"));
    /** Runs the background refresh on the calling thread, so a first call already sees its result. */
    AdminCoverageHealthServiceImpl service = new AdminCoverageHealthServiceImpl(dataQualityService,
            coverageHealthReader, 600, clock, Runnable::run);

    /** A clock the test moves forward. */
    static final class MovingClock extends Clock {
        private Instant now;

        MovingClock(Instant start) { this.now = start; }

        void advance(Duration duration) { now = now.plus(duration); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }

    /** Keeps the submitted refreshes until the test runs them, like a background thread that has not finished. */
    static final class DeferredExecutor implements java.util.concurrent.Executor {
        final List<Runnable> queued = new ArrayList<>();

        @Override public void execute(Runnable task) { queued.add(task); }

        void runAll() {
            List<Runnable> tasks = new ArrayList<>(queued);
            queued.clear();
            tasks.forEach(Runnable::run);
        }
    }

    private static final Instant GENERATED_AT = Instant.parse("2026-11-16T09:58:00Z");
    private static final Instant NOV_10 = Instant.parse("2026-11-10T02:00:00Z");
    private static final Instant NOV_12 = Instant.parse("2026-11-12T02:00:00Z");
    private static final ProviderJobStats NO_JOB = new ProviderJobStats(null, null, null, null, 0);

    private static DataQualityRuleSummaryDTO rule(DataQualityRule rule, Long count) {
        DataQualityRuleSummaryDTO summary = new DataQualityRuleSummaryDTO();
        summary.setRule(rule);
        summary.setIssueCount(count);
        return summary;
    }

    @BeforeEach
    void setUp() {
        DataQualityReportDTO report = new DataQualityReportDTO();
        report.setGeneratedAt(GENERATED_AT);
        report.setSummary(List.of(rule(DataQualityRule.SALE_DUPLICATE, 12L), rule(DataQualityRule.ASSESSMENT_STALE, 340L)));
        when(dataQualityService.getReport(null, 0, 1)).thenReturn(report);
        when(coverageHealthReader.countCoverage()).thenReturn(new CoverageCounts(1000, 990, 700, 950, 900, 400, 800));
        when(coverageHealthReader.ingestionJobStats(ImportJobType.PROPERTY_INGESTION))
                .thenReturn(new ProviderJobStats("COMPLETED", NOV_12, null, NOV_12, 0));
        when(coverageHealthReader.ingestionJobStats(ImportJobType.CSV_IMPORT)).thenReturn(NO_JOB);
        when(coverageHealthReader.imageBackfillJobStats())
                .thenReturn(new ProviderJobStats("FAILED", NOV_12, "RATE_LIMITED", NOV_10, 1));
    }

    private ProviderHealth provider(AdminCoverageHealthDTO dto, String name) {
        return dto.providers().stream().filter(p -> p.provider().equals(name)).findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("Test Quality And Coverage")
    class QualityAndCoverageTest {

        @Test
        @DisplayName("When every block is readable, then quality and coverage come from the recorded counts")
        void whenReadable_thenCountsAreReturned() {
            // Act
            AdminCoverageHealthDTO dto = service.getDashboard();

            // Assert
            assertNotNull(dto.observedAt());
            assertEquals(AdminCoverageHealthDTO.AVAILABLE, dto.quality().status());
            assertEquals(GENERATED_AT, dto.quality().generatedAt());
            assertEquals(352L, dto.quality().totalIssues());
            assertEquals(2, dto.quality().rules().size());

            assertEquals(AdminCoverageHealthDTO.AVAILABLE, dto.coverage().status());
            assertEquals(1000L, dto.coverage().totalProperties());
            assertEquals(6, dto.coverage().sources().size());
            assertEquals("SALES", dto.coverage().sources().get(1).source());
            assertEquals(700, dto.coverage().sources().get(1).propertiesCovered());
            assertEquals(0, new BigDecimal("70.00").compareTo(dto.coverage().sources().get(1).coveragePercent()));
            assertEquals(AdminCoverageHealthDTO.NOTE, dto.note());
        }

        @Test
        @DisplayName("When percentages do not divide exactly, then they are rounded to two decimals")
        void whenNotExact_thenRounded() {
            // Arrange
            when(coverageHealthReader.countCoverage()).thenReturn(new CoverageCounts(3, 1, 2, 0, 0, 0, 0));

            // Act
            AdminCoverageHealthDTO dto = service.getDashboard();

            // Assert
            assertEquals(new BigDecimal("33.33"), dto.coverage().sources().get(0).coveragePercent());
            assertEquals(new BigDecimal("66.67"), dto.coverage().sources().get(1).coveragePercent());
            assertEquals(new BigDecimal("0.00"), dto.coverage().sources().get(2).coveragePercent());
        }

        @Test
        @DisplayName("When no property is recorded, then coverage is unavailable instead of 0%")
        void whenNoProperty_thenCoverageUnavailable() {
            // Arrange
            when(coverageHealthReader.countCoverage()).thenReturn(new CoverageCounts(0, 0, 0, 0, 0, 0, 0));

            // Act
            AdminCoverageHealthDTO dto = service.getDashboard();

            // Assert
            assertEquals(AdminCoverageHealthDTO.UNAVAILABLE, dto.coverage().status());
            assertTrue(dto.coverage().sources().isEmpty());
            assertNotNull(dto.coverage().reason());
        }

        @Test
        @DisplayName("When a block cannot be read, then only that block is unavailable")
        void whenBlockUnreadable_thenOnlyThatBlockUnavailable() {
            // Arrange
            when(dataQualityService.getReport(null, 0, 1)).thenThrow(new DataAccessResourceFailureException("down"));
            when(coverageHealthReader.countCoverage()).thenThrow(new DataAccessResourceFailureException("down"));

            // Act
            AdminCoverageHealthDTO dto = service.getDashboard();

            // Assert
            assertEquals(AdminCoverageHealthDTO.UNAVAILABLE, dto.quality().status());
            assertNull(dto.quality().totalIssues());
            assertEquals(AdminCoverageHealthDTO.UNAVAILABLE, dto.coverage().status());
            assertNull(dto.coverage().totalProperties());
            assertEquals(AdminCoverageHealthDTO.HEALTHY, provider(dto, AdminCoverageHealthServiceImpl.MIAMI_DADE_API).status());
        }

        @Test
        @DisplayName("When no database connection can be had for the quality report, then only quality is unavailable")
        void whenNoConnectionForQuality_thenOnlyQualityUnavailable() {
            // Arrange
            when(dataQualityService.getReport(null, 0, 1))
                    .thenThrow(new CannotCreateTransactionException("Connection is not available"));

            // Act
            AdminCoverageHealthDTO dto = service.getDashboard();

            // Assert
            assertEquals(AdminCoverageHealthDTO.UNAVAILABLE, dto.quality().status());
            assertEquals("Data-quality findings could not be read", dto.quality().reason());
            assertEquals(AdminCoverageHealthDTO.AVAILABLE, dto.coverage().status());
        }

        @Test
        @DisplayName("When the data-quality rules have no counts yet, then quality is unavailable as being calculated")
        void whenNoQualityCountsYet_thenQualityCalculating() {
            // Arrange
            DataQualityReportDTO counting = new DataQualityReportDTO();
            counting.setCalculating(true);
            counting.setSummary(List.of(rule(DataQualityRule.SALE_DUPLICATE, null)));
            when(dataQualityService.getReport(null, 0, 1)).thenReturn(counting);

            // Act
            AdminCoverageHealthDTO dto = service.getDashboard();

            // Assert
            assertEquals(AdminCoverageHealthDTO.UNAVAILABLE, dto.quality().status());
            assertEquals(AdminCoverageHealthServiceImpl.CALCULATING, dto.quality().reason());
            assertNull(dto.quality().totalIssues());
        }

        @Test
        @DisplayName("When a rule has no count, then the total is null instead of a partial sum and the rules are listed")
        void whenRuleWithoutCount_thenTotalIsNull() {
            // Arrange
            DataQualityReportDTO partial = new DataQualityReportDTO();
            partial.setGeneratedAt(GENERATED_AT);
            partial.setSummary(List.of(rule(DataQualityRule.SALE_DUPLICATE, 12L),
                    rule(DataQualityRule.LOT_SIZE_CONFLICT, null)));
            when(dataQualityService.getReport(null, 0, 1)).thenReturn(partial);

            // Act
            AdminCoverageHealthDTO dto = service.getDashboard();

            // Assert
            assertEquals(AdminCoverageHealthDTO.AVAILABLE, dto.quality().status());
            assertNull(dto.quality().totalIssues());
            assertEquals(2, dto.quality().rules().size());
            assertEquals(GENERATED_AT, dto.quality().generatedAt());
        }
    }

    @Nested
    @DisplayName("Test Provider Health")
    class ProviderHealthTest {

        @Test
        @DisplayName("When the latest finished job completed, then the provider is healthy")
        void whenCompleted_thenHealthy() {
            // Act
            ProviderHealth health = provider(service.getDashboard(), AdminCoverageHealthServiceImpl.MIAMI_DADE_API);

            // Assert
            assertEquals(AdminCoverageHealthDTO.HEALTHY, health.status());
            assertEquals("COMPLETED", health.lastJobStatus());
            assertEquals(NOV_12, health.lastJobAt());
            assertEquals(NOV_12, health.lastSuccessAt());
            assertNull(health.reason());
        }

        @Test
        @DisplayName("When the latest finished job failed, then the provider is degraded with its error and last success")
        void whenFailed_thenDegraded() {
            // Act
            ProviderHealth health = provider(service.getDashboard(), AdminCoverageHealthServiceImpl.MAPILLARY);

            // Assert
            assertEquals(AdminCoverageHealthDTO.DEGRADED, health.status());
            assertEquals("FAILED", health.lastJobStatus());
            assertEquals("RATE_LIMITED", health.lastErrorCode());
            assertEquals(NOV_10, health.lastSuccessAt());
            assertEquals(1, health.activeJobs());
        }

        @Test
        @DisplayName("When no job is recorded, then the provider is unavailable, never healthy")
        void whenNoJob_thenUnavailable() {
            // Act
            ProviderHealth health = provider(service.getDashboard(), AdminCoverageHealthServiceImpl.CSV_IMPORT);

            // Assert
            assertEquals(AdminCoverageHealthDTO.UNAVAILABLE, health.status());
            assertNull(health.lastJobStatus());
            assertEquals("No finished job is recorded", health.reason());
        }

        @Test
        @DisplayName("When the only job is still running, then the provider is unavailable with that reason")
        void whenOnlyRunning_thenUnavailable() {
            // Arrange
            when(coverageHealthReader.ingestionJobStats(ImportJobType.CSV_IMPORT))
                    .thenReturn(new ProviderJobStats(null, null, null, null, 1));

            // Act
            ProviderHealth health = provider(service.getDashboard(), AdminCoverageHealthServiceImpl.CSV_IMPORT);

            // Assert
            assertEquals(AdminCoverageHealthDTO.UNAVAILABLE, health.status());
            assertEquals(1, health.activeJobs());
            assertEquals("The first job is still queued or running", health.reason());
        }

        @Test
        @DisplayName("When the job records cannot be read, then only that provider is unavailable")
        void whenJobsUnreadable_thenProviderUnavailable() {
            // Arrange
            when(coverageHealthReader.imageBackfillJobStats()).thenThrow(new DataAccessResourceFailureException("down"));

            // Act
            AdminCoverageHealthDTO dto = service.getDashboard();

            // Assert
            assertEquals(AdminCoverageHealthDTO.UNAVAILABLE, provider(dto, AdminCoverageHealthServiceImpl.MAPILLARY).status());
            assertEquals(AdminCoverageHealthDTO.HEALTHY, provider(dto, AdminCoverageHealthServiceImpl.MIAMI_DADE_API).status());
            assertEquals(3, dto.providers().size());
        }
    }

    @Nested
    @DisplayName("Test Background Refresh")
    class BackgroundRefreshTest {
        DeferredExecutor background = new DeferredExecutor();
        AdminCoverageHealthServiceImpl deferred = new AdminCoverageHealthServiceImpl(dataQualityService,
                coverageHealthReader, 600, clock, background);

        @Test
        @DisplayName("Before the first coverage count ends, coverage is unavailable with the reason, at once; quality "
                + "is read live from the data-quality counts")
        void whenFirstCalculationRunning_thenCalculatingWithoutWaiting() {
            // Act
            AdminCoverageHealthDTO dto = deferred.getDashboard();

            // Assert
            assertEquals(AdminCoverageHealthDTO.AVAILABLE, dto.quality().status());
            assertEquals(GENERATED_AT, dto.quality().generatedAt());
            assertEquals(AdminCoverageHealthDTO.UNAVAILABLE, dto.coverage().status());
            assertEquals(AdminCoverageHealthServiceImpl.CALCULATING, dto.coverage().reason());
            assertNull(dto.coverage().countedAt());
            assertEquals(AdminCoverageHealthDTO.HEALTHY, provider(dto, AdminCoverageHealthServiceImpl.MIAMI_DADE_API).status(),
                    "provider health is read live");
            assertEquals(1, background.queued.size());
            verify(coverageHealthReader, never()).countCoverage();
        }

        @Test
        @DisplayName("Calls while a refresh is running start no second refresh")
        void whenRefreshRunning_thenNoSecondRefresh() {
            // Act
            deferred.getDashboard();
            deferred.getDashboard();
            deferred.getDashboard();

            // Assert
            assertEquals(1, background.queued.size());
        }

        @Test
        @DisplayName("Once calculated, the counts are served from memory with their time until the refresh period ends")
        void whenCalculated_thenServedFromMemory() {
            // Arrange
            Instant countedAt = clock.instant();
            deferred.getDashboard();
            background.runAll();
            clock.advance(Duration.ofSeconds(599));

            // Act
            AdminCoverageHealthDTO dto = deferred.getDashboard();
            deferred.getDashboard();

            // Assert
            assertEquals(AdminCoverageHealthDTO.AVAILABLE, dto.coverage().status());
            assertEquals(countedAt, dto.coverage().countedAt());
            assertEquals(GENERATED_AT, dto.quality().generatedAt());
            assertEquals(clock.instant(), dto.observedAt());
            assertTrue(background.queued.isEmpty(), "no refresh within the period");
            verify(coverageHealthReader, times(1)).countCoverage();
            // quality is read on each call from the counts the data-quality service keeps in memory
            verify(dataQualityService, times(3)).getReport(null, 0, 1);
        }

        @Test
        @DisplayName("After the refresh period the old counts are returned at once and one refresh starts in the background")
        void whenOlderThanPeriod_thenOldCountsAndOneRefresh() {
            // Arrange
            Instant firstCount = clock.instant();
            deferred.getDashboard();
            background.runAll();
            clock.advance(Duration.ofSeconds(600));
            when(coverageHealthReader.countCoverage()).thenReturn(new CoverageCounts(2000, 2000, 1000, 0, 0, 0, 0));

            // Act
            AdminCoverageHealthDTO stale = deferred.getDashboard();
            background.runAll();
            AdminCoverageHealthDTO fresh = deferred.getDashboard();

            // Assert
            assertEquals(firstCount, stale.coverage().countedAt());
            assertEquals(1000L, stale.coverage().totalProperties());
            assertEquals(clock.instant(), fresh.coverage().countedAt());
            assertEquals(2000L, fresh.coverage().totalProperties());
            verify(coverageHealthReader, times(2)).countCoverage();
        }

        @Test
        @DisplayName("A coverage refresh that cannot read the records keeps the last good counts with their older time")
        void whenRefreshFails_thenLastGoodCountsKept() {
            // Arrange
            Instant firstCount = clock.instant();
            deferred.getDashboard();
            background.runAll();
            clock.advance(Duration.ofSeconds(700));
            when(coverageHealthReader.countCoverage()).thenThrow(new DataAccessResourceFailureException("down"));

            // Act
            deferred.getDashboard();
            background.runAll();
            AdminCoverageHealthDTO dto = deferred.getDashboard();

            // Assert
            assertEquals(AdminCoverageHealthDTO.AVAILABLE, dto.coverage().status());
            assertEquals(firstCount, dto.coverage().countedAt());
            assertEquals(AdminCoverageHealthDTO.AVAILABLE, dto.quality().status());
        }

        @Test
        @DisplayName("A refresh period below one second is refused")
        void whenPeriodTooShort_thenRefused() {
            assertThrows(IllegalArgumentException.class, () -> new AdminCoverageHealthServiceImpl(dataQualityService,
                    coverageHealthReader, 0, clock, background));
        }
    }

    @Nested
    @DisplayName("Test Coverage Percentages")
    class PercentageTest {

        @Test
        @DisplayName("100.00 only when every property is covered, never by rounding")
        void whenAlmostAll_thenNot100() {
            assertEquals(new BigDecimal("99.99"),
                    AdminCoverageHealthServiceImpl.source("ADDRESSES", 582_576, 582_589).coveragePercent());
            assertEquals(new BigDecimal("100.00"),
                    AdminCoverageHealthServiceImpl.source("ADDRESSES", 582_589, 582_589).coveragePercent());
        }

        @Test
        @DisplayName("0.00 only when no property is covered, never by rounding")
        void whenAlmostNone_thenNot0() {
            assertEquals(new BigDecimal("0.01"), AdminCoverageHealthServiceImpl.source("IMAGES", 1, 582_589).coveragePercent());
            assertEquals(new BigDecimal("0.00"), AdminCoverageHealthServiceImpl.source("IMAGES", 0, 582_589).coveragePercent());
        }

        @Test
        @DisplayName("Other shares keep normal rounding to two decimals")
        void whenInBetween_thenHalfUp() {
            assertEquals(new BigDecimal("94.80"), AdminCoverageHealthServiceImpl.source("SALES", 552_272, 582_589).coveragePercent());
            assertEquals(new BigDecimal("0.13"), AdminCoverageHealthServiceImpl.source("IMAGES", 777, 582_589).coveragePercent());
        }
    }
}
