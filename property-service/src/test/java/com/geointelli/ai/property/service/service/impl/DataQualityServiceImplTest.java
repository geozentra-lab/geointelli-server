package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.DataQualityIssueDTO;
import com.geointelli.ai.property.service.dto.DataQualityReportDTO;
import com.geointelli.ai.property.service.dto.DataQualityRuleSummaryDTO;
import com.geointelli.ai.property.service.enums.DataQualityCategory;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import com.geointelli.ai.property.service.repository.DataQualityIssueRow;
import com.geointelli.ai.property.service.repository.DataQualityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DataQualityServiceImplTest {

    DataQualityRepository dataQualityRepository = mock(DataQualityRepository.class);

    // 0 seconds: counts are recalculated on each request, so each test sees its own stubs.
    DataQualityServiceImpl dataQualityService =
            new DataQualityServiceImpl(dataQualityRepository, 0, Clock.systemUTC());

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
            assertEquals(12L, report.getSummary().get(DataQualityRule.SALE_DUPLICATE.ordinal()).getIssueCount());
            assertNull(report.getRule());
            assertNull(report.getIssues());
            assertNotNull(report.getGeneratedAt());
            assertEquals("Rule counts are calculated on each request; the issue page is read on each request. "
                    + "Nothing is changed or deleted.", report.getNote());
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
            assertTrue(report.getSummary().stream().allMatch(summary -> summary.getIssueCount() == 0));
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
    @DisplayName("Count Reuse Tests")
    class CountReuse {

        final Instant start = Instant.parse("2026-11-13T10:00:00Z");
        final MutableClock clock = new MutableClock(start);
        final DataQualityServiceImpl cachingService = new DataQualityServiceImpl(dataQualityRepository, 600, clock);

        @Test
        @DisplayName("When requested again within the reuse time, then reuse the counts and read the issue page again")
        void whenWithinReuseTime_thenReuseCountsAndReadIssuePageAgain() {
            // Arrange
            when(dataQualityRepository.countSaleDuplicate()).thenReturn(3L, 99L);
            cachingService.getReport(DataQualityRule.SALE_DUPLICATE, 0, 20);
            clock.advance(Duration.ofSeconds(599));

            // Act
            DataQualityReportDTO report = cachingService.getReport(DataQualityRule.SALE_DUPLICATE, 0, 20);

            // Assert
            verify(dataQualityRepository, times(1)).countSaleDuplicate();
            verify(dataQualityRepository, times(1)).countMissingAddress();
            verify(dataQualityRepository, times(2)).findSaleDuplicate(20, 0L);
            assertEquals(3L, report.getIssues().totalElements());
            assertEquals(start, report.getGeneratedAt());
            assertEquals("Rule counts were calculated at generatedAt and are reused for up to 10 minutes; "
                    + "the issue page is read on each request. Nothing is changed or deleted.", report.getNote());
        }

        @Test
        @DisplayName("When the reuse time has passed, then count every rule again")
        void whenReuseTimePassed_thenCountEveryRuleAgain() {
            // Arrange
            when(dataQualityRepository.countMissingAddress()).thenReturn(4L, 5L);
            cachingService.getReport(null, 0, 20);
            clock.advance(Duration.ofSeconds(600));

            // Act
            DataQualityReportDTO report = cachingService.getReport(null, 0, 20);

            // Assert
            verify(dataQualityRepository, times(2)).countMissingAddress();
            verify(dataQualityRepository, times(2)).countAssessmentDuplicate();
            assertEquals(5L, report.getSummary().get(0).getIssueCount());
            assertEquals(start.plusSeconds(600), report.getGeneratedAt());
        }

        @Test
        @DisplayName("When a second request arrives while counting, then it waits and reuses the same counts")
        void whenRequestArrivesWhileCounting_thenCountOnce() throws Exception {
            // Arrange
            CountDownLatch counting = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            when(dataQualityRepository.countMissingAddress()).thenAnswer(invocation -> {
                counting.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return 4L;
            });
            List<DataQualityReportDTO> reports = new CopyOnWriteArrayList<>();
            Thread first = new Thread(() -> reports.add(cachingService.getReport(null, 0, 20)));
            Thread second = new Thread(() -> reports.add(cachingService.getReport(null, 0, 20)));

            // Act
            first.start();
            assertTrue(counting.await(5, TimeUnit.SECONDS));
            second.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (second.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals(Thread.State.BLOCKED, second.getState());
            release.countDown();
            first.join(5000);
            second.join(5000);

            // Assert
            verify(dataQualityRepository, times(1)).countMissingAddress();
            assertEquals(2, reports.size());
            assertTrue(reports.stream().allMatch(report -> report.getSummary().get(0).getIssueCount() == 4L));
        }

        @Test
        @DisplayName("When the reuse time is negative, then throw IllegalArgumentException")
        void whenReuseTimeNegative_thenThrowIllegalArgumentException() {
            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> new DataQualityServiceImpl(dataQualityRepository, -1, clock));
            assertEquals("data-quality.summary-cache-seconds must be 0 or greater", exception.getMessage());
        }
    }
}
