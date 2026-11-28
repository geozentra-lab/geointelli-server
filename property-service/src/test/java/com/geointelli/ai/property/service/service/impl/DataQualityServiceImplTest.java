package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.DataQualityIssueDTO;
import com.geointelli.ai.property.service.dto.DataQualityReportDTO;
import com.geointelli.ai.property.service.dto.DataQualityRuleSummaryDTO;
import com.geointelli.ai.property.service.enums.DataQualityCategory;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import com.geointelli.ai.property.service.repository.DataQualityIssueRow;
import com.geointelli.ai.property.service.repository.DataQualityRepository;
import com.geointelli.ai.property.service.service.DataQualityReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DataQualityServiceImplTest {

    DataQualityRepository dataQualityRepository = mock(DataQualityRepository.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    // 0 seconds and a recount on the calling thread: each request counts again, so each test sees its own stubs.
    DataQualityServiceImpl dataQualityService = new DataQualityServiceImpl(dataQualityRepository,
            mock(DataQualityReviewService.class), transactionManager, 0, 120, Clock.systemUTC(), Runnable::run);

    /** Clock moved by the tests to expire reused counts. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static DataQualityIssueRow row(long entityId, long propertyId, String detail,
                                           String sourceCounty, String sourceState, Long sourcePropertyId) {
        return new DataQualityIssueRow() {
            @Override public Long getEntityId() { return entityId; }
            @Override public Long getPropertyId() { return propertyId; }
            @Override public String getFolio() { return "F" + propertyId; }
            @Override public String getDetail() { return detail; }
            @Override public String getSourceCounty() { return sourceCounty; }
            @Override public String getSourceState() { return sourceState; }
            @Override public Long getSourcePropertyId() { return sourcePropertyId; }
        };
    }

    private static List<DataQualityIssueRow> rows(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(id -> row(id, id, "Property lot size 1000 vs parcel lot size 2000", null, null, null))
                .toList();
    }

    private static DataQualityRuleSummaryDTO summary(DataQualityReportDTO report, DataQualityRule rule) {
        return report.getSummary().get(rule.ordinal());
    }

    @Nested
    @DisplayName("GetReport Tests")
    class GetReport {

        @Test
        @DisplayName("When no rule is given, then return the summary of every rule only")
        void whenNoRule_thenReturnSummaryOnly() {
            // Arrange
            when(dataQualityRepository.countMissingAddress()).thenReturn(4L);
            when(dataQualityRepository.countSaleDuplicate()).thenReturn(12L);

            // Act
            DataQualityReportDTO report = dataQualityService.getReport(null, 0, 20);

            // Assert
            assertEquals(Arrays.asList(DataQualityRule.values()),
                    report.getSummary().stream().map(DataQualityRuleSummaryDTO::getRule).toList());
            DataQualityRuleSummaryDTO missingAddress = report.getSummary().get(0);
            assertEquals(DataQualityCategory.MISSING, missingAddress.getCategory());
            assertEquals("PROPERTY", missingAddress.getEntityType());
            assertEquals("addresses", missingAddress.getSource());
            assertEquals(DataQualityRule.PROPERTY_MISSING_ADDRESS.getDescription(), missingAddress.getDescription());
            assertEquals(4L, missingAddress.getIssueCount());
            assertNull(missingAddress.getUnavailableReason());
            assertEquals(12L, summary(report, DataQualityRule.SALE_DUPLICATE).getIssueCount());
            assertNull(report.getRule());
            assertNull(report.getIssues());
            assertNotNull(report.getGeneratedAt());
            assertFalse(report.isCalculating());
            assertEquals("Rule counts are recalculated in the background on each request; generatedAt shows when "
                    + "the returned counts were calculated. Before the first count, issueCount is null and "
                    + "calculating is true. The issue page is read on each request. Nothing is changed or deleted.",
                    report.getNote());
            verify(dataQualityRepository, never()).findMissingAddress(anyInt(), anyLong());
            verify(dataQualityRepository, never()).findSaleDuplicate(anyInt(), anyLong());
        }

        @Test
        @DisplayName("When a rule is given, then return its page of issues with a reproducible reference and provenance")
        void whenRuleGiven_thenReturnIssuesWithReferenceAndProvenance() {
            // Arrange
            when(dataQualityRepository.countSaleDuplicate()).thenReturn(45L);
            when(dataQualityRepository.findSaleDuplicate(20, 20L)).thenReturn(List.of(
                    row(9012L, 345L, "Same deed 09608/1730, date and price as sale 8800", "Miami-Dade", "FL", 123456L),
                    row(9013L, 346L, "Same deed 24611/3579, date and price as sale 8801", null, null, null)));

            // Act
            DataQualityReportDTO report = dataQualityService.getReport(DataQualityRule.SALE_DUPLICATE, 1, 20);

            // Assert
            assertEquals(DataQualityRule.SALE_DUPLICATE, report.getRule());
            DataQualityIssueDTO issue = report.getIssues().content().get(0);
            assertEquals("SALE_DUPLICATE:SALE:9012", issue.getReference());
            assertEquals(DataQualityRule.SALE_DUPLICATE, issue.getRule());
            assertEquals(DataQualityCategory.DUPLICATE, issue.getCategory());
            assertEquals("SALE", issue.getEntityType());
            assertEquals(9012L, issue.getEntityId());
            assertEquals(345L, issue.getPropertyId());
            assertEquals("F345", issue.getFolio());
            assertEquals("Same deed 09608/1730, date and price as sale 8800", issue.getDetail());
            assertEquals("Miami-Dade", issue.getSourceCounty());
            assertEquals("FL", issue.getSourceState());
            assertEquals(123456L, issue.getSourcePropertyId());

            DataQualityIssueDTO withoutMapping = report.getIssues().content().get(1);
            assertNull(withoutMapping.getSourceCounty());
            assertNull(withoutMapping.getSourcePropertyId());

            assertEquals(1, report.getIssues().page());
            assertEquals(20, report.getIssues().size());
            assertEquals(45L, report.getIssues().totalElements());
            assertEquals(3L, report.getIssues().totalPages());
            assertFalse(report.getIssues().first());
            assertFalse(report.getIssues().last());
        }

        @Test
        @DisplayName("When the rule has no issue, then return zero counts and an empty last page")
        void whenNoIssue_thenReturnEmptyPage() {
            // Act
            DataQualityReportDTO report = dataQualityService.getReport(DataQualityRule.ASSESSMENT_STALE, 0, 20);

            // Assert
            assertTrue(report.getSummary().stream().allMatch(summary -> summary.getIssueCount() == 0L));
            assertTrue(report.getIssues().content().isEmpty());
            assertEquals(0L, report.getIssues().totalElements());
            assertEquals(0L, report.getIssues().totalPages());
            assertTrue(report.getIssues().first());
            assertTrue(report.getIssues().last());
        }

        @Test
        @DisplayName("When the page is past the end, then return an empty last page")
        void whenPagePastEnd_thenReturnEmptyLastPage() {
            // Arrange
            when(dataQualityRepository.countMissingParcel()).thenReturn(5L);

            // Act
            DataQualityReportDTO report = dataQualityService.getReport(DataQualityRule.PROPERTY_MISSING_PARCEL, 3, 20);

            // Assert
            verify(dataQualityRepository).findMissingParcel(20, 60L);
            assertTrue(report.getIssues().content().isEmpty());
            assertEquals(5L, report.getIssues().totalElements());
            assertTrue(report.getIssues().last());
        }

        @ParameterizedTest
        @EnumSource(DataQualityRule.class)
        @DisplayName("When any rule is requested, then it is counted and listed")
        void whenAnyRuleRequested_thenReturnItsPage(DataQualityRule rule) {
            // Act
            DataQualityReportDTO report = dataQualityService.getReport(rule, 0, 10);

            // Assert
            assertEquals(rule, report.getRule());
            assertNotNull(report.getIssues());
            assertEquals(DataQualityRule.values().length, report.getSummary().size());
        }

        @Test
        @DisplayName("When page or size is out of range, then throw IllegalArgumentException without querying")
        void whenPageOrSizeIsInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            IllegalArgumentException page = assertThrows(IllegalArgumentException.class,
                    () -> dataQualityService.getReport(null, -1, 20));
            assertEquals("page must be 0 or greater", page.getMessage());

            IllegalArgumentException size = assertThrows(IllegalArgumentException.class,
                    () -> dataQualityService.getReport(DataQualityRule.SALE_DUPLICATE, 0, 0));
            assertEquals("size must be between 1 and 100", size.getMessage());

            assertThrows(IllegalArgumentException.class,
                    () -> dataQualityService.getReport(null, 0, DataQualityServiceImpl.MAX_PAGE_SIZE + 1));
            verifyNoInteractions(dataQualityRepository);
        }
    }

    @Nested
    @DisplayName("Rule Count Failure Tests")
    class RuleCountFailure {

        @Test
        @DisplayName("Each rule is counted in its own read-only transaction limited to the count timeout")
        void eachRuleIsCountedInItsOwnTimeLimitedReadOnlyTransaction() {
            // Act
            dataQualityService.getReport(null, 0, 20);

            // Assert
            ArgumentCaptor<TransactionDefinition> definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
            verify(transactionManager, times(DataQualityRule.values().length)).getTransaction(definitions.capture());
            assertTrue(definitions.getAllValues().stream().allMatch(TransactionDefinition::isReadOnly));
            assertTrue(definitions.getAllValues().stream().allMatch(definition -> definition.getTimeout() == 120));
        }

        @Test
        @DisplayName("When a rule's count times out, then it has no count and a reason, and the other rules keep theirs")
        void whenRuleCountTimesOut_thenOnlyThatRuleIsUnavailable() {
            // Arrange
            when(dataQualityRepository.countMissingAddress()).thenReturn(4L);
            when(dataQualityRepository.countLotSizeConflict()).thenThrow(new QueryTimeoutException("canceled"));

            // Act
            DataQualityReportDTO report = dataQualityService.getReport(null, 0, 20);

            // Assert
            DataQualityRuleSummaryDTO lotSize = summary(report, DataQualityRule.LOT_SIZE_CONFLICT);
            assertNull(lotSize.getIssueCount());
            assertEquals("Counting took longer than 120 seconds and was stopped", lotSize.getUnavailableReason());
            assertEquals(4L, summary(report, DataQualityRule.PROPERTY_MISSING_ADDRESS).getIssueCount());
            assertNotNull(report.getGeneratedAt());
            verify(dataQualityRepository).countAssessmentDuplicate();
        }

        @Test
        @DisplayName("When a rule's count fails, then it has no count, never a guessed 0, and the reason says so")
        void whenRuleCountFails_thenItHasNoCount() {
            // Arrange
            when(dataQualityRepository.countSaleDuplicate()).thenThrow(new DataAccessResourceFailureException("down"));

            // Act
            DataQualityReportDTO report = dataQualityService.getReport(null, 0, 20);

            // Assert
            assertNull(summary(report, DataQualityRule.SALE_DUPLICATE).getIssueCount());
            assertEquals("Could not be counted", summary(report, DataQualityRule.SALE_DUPLICATE).getUnavailableReason());
            assertEquals(0L, summary(report, DataQualityRule.ASSESSMENT_DUPLICATE).getIssueCount());
        }

        @Test
        @DisplayName("When the requested rule has no count, then one more row tells whether a next page exists")
        void whenRuleHasNoCount_thenNextPageIsKnownFromOneMoreRow() {
            // Arrange
            when(dataQualityRepository.countLotSizeConflict()).thenThrow(new QueryTimeoutException("canceled"));
            when(dataQualityRepository.findLotSizeConflict(21, 20L)).thenReturn(rows(21));

            // Act
            DataQualityReportDTO report = dataQualityService.getReport(DataQualityRule.LOT_SIZE_CONFLICT, 1, 20);

            // Assert
            assertEquals(20, report.getIssues().content().size());
            assertEquals(41L, report.getIssues().totalElements());
            assertEquals(3L, report.getIssues().totalPages());
            assertFalse(report.getIssues().last());
        }

        @Test
        @DisplayName("When the requested rule has no count and the page is not full, then it is the last page")
        void whenRuleHasNoCountAndPageNotFull_thenItIsTheLastPage() {
            // Arrange
            when(dataQualityRepository.countLotSizeConflict()).thenThrow(new QueryTimeoutException("canceled"));
            when(dataQualityRepository.findLotSizeConflict(21, 0L)).thenReturn(rows(7));

            // Act
            DataQualityReportDTO report = dataQualityService.getReport(DataQualityRule.LOT_SIZE_CONFLICT, 0, 20);

            // Assert
            assertEquals(7, report.getIssues().content().size());
            assertEquals(7L, report.getIssues().totalElements());
            assertEquals(1L, report.getIssues().totalPages());
            assertTrue(report.getIssues().last());
        }
    }

    @Nested
    @DisplayName("Background Count Tests")
    class BackgroundCount {

        final Instant start = Instant.parse("2026-11-13T10:00:00Z");
        final MutableClock clock = new MutableClock(start);
        final List<Runnable> tasks = Collections.synchronizedList(new ArrayList<>());
        final DataQualityServiceImpl queuedService = new DataQualityServiceImpl(dataQualityRepository,
                mock(DataQualityReviewService.class), transactionManager, 600, 120, clock, tasks::add);

        private void runQueuedCounts() {
            List<Runnable> queued = List.copyOf(tasks);
            tasks.clear();
            queued.forEach(Runnable::run);
        }

        @Test
        @DisplayName("Before the first count ends, the report comes back at once without counts, marked as calculating")
        void beforeFirstCount_thenReturnAtOnceWithoutCounts() {
            // Act
            DataQualityReportDTO report = queuedService.getReport(null, 0, 20);

            // Assert
            assertTrue(report.isCalculating());
            assertNull(report.getGeneratedAt());
            assertTrue(report.getSummary().stream().allMatch(summary -> summary.getIssueCount() == null));
            assertTrue(report.getSummary().stream()
                    .allMatch(summary -> DataQualityServiceImpl.CALCULATING.equals(summary.getUnavailableReason())));
            assertEquals(1, tasks.size());
            verifyNoInteractions(dataQualityRepository);
        }

        @Test
        @DisplayName("Requests while a count is running start no second count")
        void whileCounting_thenNoSecondCount() {
            // Act
            queuedService.getReport(null, 0, 20);
            queuedService.getReport(null, 0, 20);
            queuedService.getReport(DataQualityRule.SALE_DUPLICATE, 0, 20);

            // Assert
            assertEquals(1, tasks.size());
            verify(dataQualityRepository, never()).countSaleDuplicate();
        }

        @Test
        @DisplayName("Once counted, the counts are reused with their time and the issue page is read again")
        void whenWithinReuseTime_thenReuseCountsAndReadIssuePageAgain() {
            // Arrange
            when(dataQualityRepository.countSaleDuplicate()).thenReturn(3L, 99L);
            queuedService.getReport(DataQualityRule.SALE_DUPLICATE, 0, 20);
            runQueuedCounts();
            clock.advance(Duration.ofSeconds(599));

            // Act
            DataQualityReportDTO report = queuedService.getReport(DataQualityRule.SALE_DUPLICATE, 0, 20);

            // Assert
            assertTrue(tasks.isEmpty());
            assertFalse(report.isCalculating());
            verify(dataQualityRepository, times(1)).countSaleDuplicate();
            verify(dataQualityRepository, times(1)).countMissingAddress();
            verify(dataQualityRepository).findSaleDuplicate(20, 0L);
            assertEquals(3L, report.getIssues().totalElements());
            assertEquals(start, report.getGeneratedAt());
            assertEquals("Rule counts were calculated at generatedAt and are reused for up to 10 minutes, then "
                    + "recalculated in the background. Before the first count, issueCount is null and calculating is "
                    + "true. The issue page is read on each request. Nothing is changed or deleted.", report.getNote());
        }

        @Test
        @DisplayName("After the reuse time, the old counts come back at once and one recount starts in the background")
        void whenReuseTimePassed_thenReturnOldCountsAndRecountInBackground() {
            // Arrange
            when(dataQualityRepository.countMissingAddress()).thenReturn(4L, 5L);
            queuedService.getReport(null, 0, 20);
            runQueuedCounts();
            clock.advance(Duration.ofSeconds(600));

            // Act
            DataQualityReportDTO stale = queuedService.getReport(null, 0, 20);
            runQueuedCounts();
            DataQualityReportDTO fresh = queuedService.getReport(null, 0, 20);

            // Assert
            assertTrue(stale.isCalculating());
            assertEquals(4L, stale.getSummary().get(0).getIssueCount());
            assertEquals(start, stale.getGeneratedAt());
            assertFalse(fresh.isCalculating());
            assertEquals(5L, fresh.getSummary().get(0).getIssueCount());
            assertEquals(start.plusSeconds(600), fresh.getGeneratedAt());
            verify(dataQualityRepository, times(2)).countAssessmentDuplicate();
        }

        @Test
        @DisplayName("A request never waits for a count running on another thread")
        void whileCountingOnAnotherThread_thenRequestDoesNotWait() throws Exception {
            // Arrange
            CountDownLatch counting = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            when(dataQualityRepository.countMissingAddress()).thenAnswer(invocation -> {
                counting.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                return 4L;
            });
            ExecutorService background = Executors.newSingleThreadExecutor();
            DataQualityServiceImpl service = new DataQualityServiceImpl(dataQualityRepository,
                    mock(DataQualityReviewService.class), transactionManager, 600, 120, clock, background);
            try {
                service.getReport(null, 0, 20);
                assertTrue(counting.await(5, TimeUnit.SECONDS));

                // Act
                DataQualityReportDTO whileCounting = assertTimeoutPreemptively(Duration.ofSeconds(2),
                        () -> service.getReport(null, 0, 20));
                release.countDown();
                background.shutdown();
                assertTrue(background.awaitTermination(5, TimeUnit.SECONDS));
                DataQualityReportDTO afterCounting = service.getReport(null, 0, 20);

                // Assert
                assertTrue(whileCounting.isCalculating());
                assertNull(whileCounting.getSummary().get(0).getIssueCount());
                assertFalse(afterCounting.isCalculating());
                assertEquals(4L, afterCounting.getSummary().get(0).getIssueCount());
                verify(dataQualityRepository, times(1)).countMissingAddress();
            } finally {
                release.countDown();
                background.shutdownNow();
            }
        }

        @Test
        @DisplayName("When the reuse time is negative or the count timeout below 1, then throw IllegalArgumentException")
        void whenSettingsOutOfRange_thenThrowIllegalArgumentException() {
            // Act & Assert
            IllegalArgumentException reuse = assertThrows(IllegalArgumentException.class,
                    () -> new DataQualityServiceImpl(dataQualityRepository, mock(DataQualityReviewService.class),
                            transactionManager, -1, 120, clock, Runnable::run));
            assertEquals("data-quality.summary-cache-seconds must be 0 or greater", reuse.getMessage());

            IllegalArgumentException timeout = assertThrows(IllegalArgumentException.class,
                    () -> new DataQualityServiceImpl(dataQualityRepository, mock(DataQualityReviewService.class),
                            transactionManager, 600, 0, clock, Runnable::run));
            assertEquals("data-quality.count-timeout-seconds must be at least 1", timeout.getMessage());
        }
    }
}
