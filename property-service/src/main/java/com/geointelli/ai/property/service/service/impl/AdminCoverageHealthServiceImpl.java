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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Reads each block separately, so a block whose records cannot be read is shown as UNAVAILABLE while the other
 * blocks are still returned. Quality counts are reused from the data-quality scan (SCRUM-189).
 */
@Service
@RequiredArgsConstructor
public class AdminCoverageHealthServiceImpl implements AdminCoverageHealthService {

    static final String MIAMI_DADE_API = "MIAMI_DADE_API";
    static final String CSV_IMPORT = "CSV_IMPORT";
    static final String MAPILLARY = "MAPILLARY";

    private final DataQualityService dataQualityService;
    private final CoverageHealthReader coverageHealthReader;

    @Override
    public AdminCoverageHealthDTO getDashboard() {
        Instant observedAt = Instant.now();
        return new AdminCoverageHealthDTO(observedAt, quality(), coverage(), providers(), AdminCoverageHealthDTO.NOTE);
    }

    /** Rule counts of the data-quality scan; the summary only, no issue page. */
    private Quality quality() {
        DataQualityReportDTO report;
        try {
            report = dataQualityService.getReport(null, 0, 1);
        } catch (DataAccessException e) {
            return new Quality(AdminCoverageHealthDTO.UNAVAILABLE, null, null, List.of(),
                    "Data-quality findings could not be read");
        }
        long totalIssues = 0;
        for (DataQualityRuleSummaryDTO rule : report.getSummary()) {
            totalIssues += rule.getIssueCount();
        }
        return new Quality(AdminCoverageHealthDTO.AVAILABLE, report.getGeneratedAt(), totalIssues,
                report.getSummary(), null);
    }

    /** Properties covered by each source; percentages need at least one property. */
    private Coverage coverage() {
        CoverageCounts counts;
        try {
            counts = coverageHealthReader.countCoverage();
        } catch (DataAccessException e) {
            return new Coverage(AdminCoverageHealthDTO.UNAVAILABLE, null, List.of(),
                    "Coverage counts could not be read");
        }
        long total = counts.totalProperties();
        if (total == 0) {
            return new Coverage(AdminCoverageHealthDTO.UNAVAILABLE, 0L, List.of(), "No property is recorded");
        }
        List<SourceCoverage> sources = List.of(
                source("ADDRESSES", counts.addresses(), total),
                source("SALES", counts.sales(), total),
                source("ASSESSMENTS", counts.assessments(), total),
                source("TAXES", counts.taxes(), total),
                source("IMAGES", counts.images(), total),
                source("VALUATIONS", counts.valuations(), total));
        return new Coverage(AdminCoverageHealthDTO.AVAILABLE, total, sources, null);
    }

    private static SourceCoverage source(String name, long covered, long total) {
        BigDecimal percent = BigDecimal.valueOf(covered).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new SourceCoverage(name, covered, percent);
    }

    /** One entry per supported provider, from its recorded jobs. */
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
