package com.geointelli.ai.property.service.repository;

import com.geointelli.ai.property.service.enums.ImportJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * SQL counts behind {@link CoverageHealthReader}. A property is covered by a source when the source has at least one
 * row for it; a valuation counts only with a predicted price. A finished job is any job that is not QUEUED or
 * RUNNING, dated by its finish time (its creation time if it never recorded one).
 */
@Repository
@RequiredArgsConstructor
public class JdbcCoverageHealthReader implements CoverageHealthReader {

    private static final String COVERAGE = """
            SELECT (SELECT COUNT(*) FROM properties) AS total_properties,
                (SELECT COUNT(*) FROM properties p
                    WHERE EXISTS (SELECT 1 FROM addresses x WHERE x.property_id = p.id)) AS addresses,
                (SELECT COUNT(*) FROM properties p
                    WHERE EXISTS (SELECT 1 FROM sales x WHERE x.property_id = p.id)) AS sales,
                (SELECT COUNT(*) FROM properties p
                    WHERE EXISTS (SELECT 1 FROM assessments x WHERE x.property_id = p.id)) AS assessments,
                (SELECT COUNT(*) FROM properties p
                    WHERE EXISTS (SELECT 1 FROM taxes x WHERE x.property_id = p.id)) AS taxes,
                (SELECT COUNT(*) FROM properties p
                    WHERE EXISTS (SELECT 1 FROM property_images x WHERE x.property_id = p.id)) AS images,
                (SELECT COUNT(*) FROM properties p
                    WHERE EXISTS (SELECT 1 FROM property_value_predictions x
                        WHERE x.property_id = p.id AND x.predicted_price IS NOT NULL)) AS valuations
            """;

    private static final String INGESTION_JOBS = """
            SELECT latest.status AS last_job_status, latest.job_at AS last_job_at, latest.error_code AS last_error_code,
                (SELECT MAX(j.finished_at) FROM ingestion_jobs j
                    WHERE j.job_type = ? AND j.status = 'COMPLETED') AS last_success_at,
                (SELECT COUNT(*) FROM ingestion_jobs j
                    WHERE j.job_type = ? AND j.status IN ('QUEUED', 'RUNNING')) AS active_jobs
            FROM (SELECT 1) AS one
            LEFT JOIN LATERAL (
                SELECT j.status, COALESCE(j.finished_at, j.created_at) AS job_at, j.error_code
                FROM ingestion_jobs j
                WHERE j.job_type = ? AND j.status NOT IN ('QUEUED', 'RUNNING')
                ORDER BY job_at DESC
                LIMIT 1) AS latest ON TRUE
            """;

    private static final String IMAGE_BACKFILL_JOBS = """
            SELECT latest.status AS last_job_status, latest.job_at AS last_job_at, latest.error_code AS last_error_code,
                (SELECT MAX(j.finished_at) FROM image_backfill_jobs j
                    WHERE j.status = 'COMPLETED') AS last_success_at,
                (SELECT COUNT(*) FROM image_backfill_jobs j
                    WHERE j.status IN ('QUEUED', 'RUNNING')) AS active_jobs
            FROM (SELECT 1) AS one
            LEFT JOIN LATERAL (
                SELECT j.status, COALESCE(j.finished_at, j.created_at) AS job_at, j.error_code
                FROM image_backfill_jobs j
                WHERE j.status NOT IN ('QUEUED', 'RUNNING')
                ORDER BY job_at DESC
                LIMIT 1) AS latest ON TRUE
            """;

    private final JdbcTemplate jdbc;

    @Override
    public CoverageCounts countCoverage() {
        return jdbc.queryForObject(COVERAGE, (rs, row) -> new CoverageCounts(
                rs.getLong("total_properties"), rs.getLong("addresses"), rs.getLong("sales"),
                rs.getLong("assessments"), rs.getLong("taxes"), rs.getLong("images"), rs.getLong("valuations")));
    }

    @Override
    public ProviderJobStats ingestionJobStats(ImportJobType jobType) {
        String type = jobType.name();
        // The job type appears three times in the query, in this order
        return jdbc.queryForObject(INGESTION_JOBS, (rs, row) -> jobStats(rs), type, type, type);
    }

    @Override
    public ProviderJobStats imageBackfillJobStats() {
        return jdbc.queryForObject(IMAGE_BACKFILL_JOBS, (rs, row) -> jobStats(rs));
    }

    private static ProviderJobStats jobStats(ResultSet rs) throws SQLException {
        return new ProviderJobStats(rs.getString("last_job_status"), instant(rs, "last_job_at"),
                rs.getString("last_error_code"), instant(rs, "last_success_at"), rs.getLong("active_jobs"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
