package com.geointelli.ai.property.service.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.IngestionDataset;

/**
 * Persists ingestion jobs. Every progress write is fenced by worker ID, RUNNING status and an unexpired
 * lease, compared against the database clock, so it updates zero rows once another worker owns the job.
 * The queries use only SQL that PostgreSQL and the H2 test database share.
 */
public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    /** Oldest queued jobs this instance may run: shared inputs, or local inputs held by this instance. */
    @Query(value = """
        SELECT CAST(id AS VARCHAR) FROM ingestion_jobs
        WHERE status = 'QUEUED' AND (input_storage <> 'LOCAL' OR input_scope = CAST(:scope AS VARCHAR))
        ORDER BY created_at, id LIMIT :limit
        """, nativeQuery = true)
    List<String> findQueuedIds(@Param("scope") String scope, @Param("limit") int limit);

    /** Claims a queued job; returns 1 for the one worker that wins. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE ingestion_jobs SET status = 'RUNNING', worker_id = :worker, started_at = CURRENT_TIMESTAMP,
            lease_expires_at = :leaseUntil
        WHERE id = :id AND status = 'QUEUED'
        """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("worker") String worker, @Param("leaseUntil") Instant leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE ingestion_jobs SET lease_expires_at = :leaseUntil
        WHERE id = :id AND worker_id = :worker AND status = 'RUNNING' AND lease_expires_at > CURRENT_TIMESTAMP
        """, nativeQuery = true)
    int renewLease(@Param("id") UUID id, @Param("worker") String worker, @Param("leaseUntil") Instant leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE ingestion_jobs SET total = :total, processed = :processed, succeeded = :succeeded,
            skipped = :skipped, failed = :failed, current_step = :step, checkpoint_json = :checkpoint,
            lease_expires_at = :leaseUntil
        WHERE id = :id AND worker_id = :worker AND status = 'RUNNING' AND lease_expires_at > CURRENT_TIMESTAMP
        """, nativeQuery = true)
    int recordProgress(@Param("id") UUID id, @Param("worker") String worker, @Param("total") int total,
        @Param("processed") int processed, @Param("succeeded") int succeeded, @Param("skipped") int skipped,
        @Param("failed") int failed, @Param("step") String step, @Param("checkpoint") String checkpoint,
        @Param("leaseUntil") Instant leaseUntil);

    /** Final status; no lease check so a worker that lost its lease can still record why it stopped. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE ingestion_jobs SET status = :status, error_code = :errorCode, error_message = :errorMessage,
            finished_at = CURRENT_TIMESTAMP
        WHERE id = :id AND worker_id = :worker AND status = 'RUNNING'
        """, nativeQuery = true)
    int finish(@Param("id") UUID id, @Param("worker") String worker, @Param("status") String status,
        @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    /** Running jobs whose lease lapsed, and local-input jobs nobody claimed before their deadline. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE ingestion_jobs SET status = 'INTERRUPTED', finished_at = CURRENT_TIMESTAMP,
            error_code = CASE WHEN status = 'RUNNING' THEN 'WORKER_LEASE_EXPIRED' ELSE 'QUEUE_DEADLINE_EXPIRED' END
        WHERE status IN ('QUEUED', 'RUNNING') AND lease_expires_at IS NOT NULL
          AND lease_expires_at <= CURRENT_TIMESTAMP
        """, nativeQuery = true)
    int interruptExpired();

    boolean existsByDatasetAndStatusIn(IngestionDataset dataset, Collection<String> statuses);

    boolean existsByRetryOfJobIdAndStatusIn(UUID retryOfJobId, Collection<String> statuses);

    /**
     * Common shape of backfill and ingestion jobs in the merged admin listing. Timestamps keep the driver's
     * type (OffsetDateTime, Instant or Timestamp); the mapper converts them.
     */
    record ImportJobRow(String id, String jobType, String dataset, Long countyId, String status, int total,
            int processed, int succeeded, int skipped, int failed, String errorCode, String errorMessage,
            String currentStep, String sourceCounty, String sourceState, String inputStorage, String inputScope,
            Integer attempt, String retryOfJobId, boolean hasActiveRetry, Object createdAt, Object startedAt,
            Object finishedAt) {

        /** Builds a row from the columns of {@link #ALL_JOBS}, in order. */
        public static ImportJobRow from(Object[] columns) {
            return new ImportJobRow((String) columns[0], (String) columns[1], (String) columns[2],
                number(columns[3]) == null ? null : number(columns[3]).longValue(), (String) columns[4],
                integer(columns[5]), integer(columns[6]), integer(columns[7]), integer(columns[8]), integer(columns[9]),
                (String) columns[10], (String) columns[11], (String) columns[12], (String) columns[13],
                (String) columns[14], (String) columns[15], (String) columns[16],
                number(columns[17]) == null ? null : number(columns[17]).intValue(), (String) columns[18],
                Boolean.TRUE.equals(columns[19]), columns[20], columns[21], columns[22]);
        }

        private static Number number(Object value) { return (Number) value; }

        private static int integer(Object value) { return value == null ? 0 : ((Number) value).intValue(); }
    }

    String ALL_JOBS = """
        SELECT CAST(j.id AS VARCHAR) AS id, 'MAPILLARY_IMAGE_BACKFILL' AS job_type, CAST(NULL AS VARCHAR) AS dataset,
            j.county_id, j.status, j.total, j.processed, j.completed AS succeeded, j.skipped, j.failed,
            j.error_code, CAST(NULL AS VARCHAR) AS error_message, CAST(NULL AS VARCHAR) AS current_step,
            CAST(NULL AS VARCHAR) AS source_county, CAST(NULL AS VARCHAR) AS source_state,
            CAST(NULL AS VARCHAR) AS input_storage, CAST(NULL AS VARCHAR) AS input_scope,
            CAST(NULL AS INTEGER) AS attempt, CAST(NULL AS VARCHAR) AS retry_of_job_id, FALSE AS has_active_retry,
            j.created_at, CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS started_at, j.finished_at
        FROM image_backfill_jobs j
        UNION ALL
        SELECT CAST(i.id AS VARCHAR), i.job_type, i.dataset, i.county_id, i.status, i.total, i.processed,
            i.succeeded, i.skipped, i.failed, i.error_code, i.error_message, i.current_step,
            i.source_county, i.source_state, i.input_storage, i.input_scope, i.attempt,
            CAST(i.retry_of_job_id AS VARCHAR),
            EXISTS (SELECT 1 FROM ingestion_jobs r WHERE r.retry_of_job_id = i.id AND r.status IN ('QUEUED', 'RUNNING')),
            i.created_at, i.started_at, i.finished_at
        FROM ingestion_jobs i
        """;

    /** Rows of {@link #ALL_JOBS}; convert each with {@link ImportJobRow#from(Object[])}. */
    @Query(value = "SELECT * FROM (" + ALL_JOBS + """
        ) jobs
        WHERE (CAST(:type AS VARCHAR) IS NULL OR jobs.job_type = CAST(:type AS VARCHAR))
          AND (CAST(:status AS VARCHAR) IS NULL OR jobs.status = CAST(:status AS VARCHAR))
        ORDER BY jobs.created_at DESC, jobs.id DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<Object[]> listAllJobs(@Param("type") String type, @Param("status") String status,
        @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = "SELECT COUNT(*) FROM (" + ALL_JOBS + """
        ) jobs
        WHERE (CAST(:type AS VARCHAR) IS NULL OR jobs.job_type = CAST(:type AS VARCHAR))
          AND (CAST(:status AS VARCHAR) IS NULL OR jobs.status = CAST(:status AS VARCHAR))
        """, nativeQuery = true)
    long countAllJobs(@Param("type") String type, @Param("status") String status);
}
