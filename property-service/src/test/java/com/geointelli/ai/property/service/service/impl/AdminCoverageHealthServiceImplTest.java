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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AdminCoverageHealthServiceImplTest {

    DataQualityService dataQualityService = mock(DataQualityService.class);
    CoverageHealthReader coverageHealthReader = mock(CoverageHealthReader.class);
    AdminCoverageHealthServiceImpl service = new AdminCoverageHealthServiceImpl(dataQualityService, coverageHealthReader);

    private static final Instant GENERATED_AT = Instant.parse("2026-11-16T09:58:00Z");
    private static final Instant NOV_10 = Instant.parse("2026-11-10T02:00:00Z");
    private static final Instant NOV_12 = Instant.parse("2026-11-12T02:00:00Z");
    private static final ProviderJobStats NO_JOB = new ProviderJobStats(null, null, null, null, 0);

    private static DataQualityRuleSummaryDTO rule(DataQualityRule rule, long count) {
        DataQualityRuleSummaryDTO summary = new DataQualityRuleSummaryDTO();
        summary.setRule(rule);
        summary.setIssueCount(count);
        return summary;
    }

    @BeforeEach
    void setUp() {
        DataQualityReportDTO report = new DataQualityReportDTO();
        report.setGeneratedAt(GENERATED_AT);
        report.setSummary(List.of(rule(DataQualityRule.SALE_DUPLICATE, 12), rule(DataQualityRule.ASSESSMENT_STALE, 340)));
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
}
