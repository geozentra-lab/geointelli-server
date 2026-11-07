package com.geointelli.ai.property.service.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.enums.IngestionJobStatus;
import com.geointelli.ai.property.service.repository.IngestionJobRepository.ImportJobRow;

/**
 * Runs the V30 schema and the fenced job queries against an isolated H2 PostgreSQL-mode database:
 * atomic claiming, lease fencing, expiry and the merged admin listing.
 */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=jdbc:h2:mem:ingestion-jobs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IngestionJobRepositoryTest {
    private static final Duration LEASE = Duration.ofMinutes(10);

    @Autowired
    private IngestionJobRepository jobs;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    void createSchema() throws Exception {
        jdbc.execute("CREATE TABLE counties (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE image_backfill_jobs (id BIGINT PRIMARY KEY, county_id BIGINT, status VARCHAR(20), "
            + "total INTEGER DEFAULT 0, processed INTEGER DEFAULT 0, completed INTEGER DEFAULT 0, skipped INTEGER DEFAULT 0, "
            + "failed INTEGER DEFAULT 0, error_code VARCHAR(80), created_at TIMESTAMP WITH TIME ZONE, finished_at TIMESTAMP WITH TIME ZONE)");
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V30__add_ingestion_jobs.sql"));
        }
        jdbc.update("INSERT INTO counties VALUES (1)");
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM ingestion_job_items");
        jdbc.update("DELETE FROM ingestion_jobs");
        jdbc.update("DELETE FROM image_backfill_jobs");
    }

    private UUID job(String status, String storage, String scope, Instant createdAt, Instant lease) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ingestion_jobs (id, job_type, dataset, status, input_storage, input_scope, created_at, lease_expires_at) "
            + "VALUES (?, 'CSV_IMPORT', 'ADDRESSES', ?, ?, ?, ?, ?)", id, status, storage, scope, Timestamp.from(createdAt),
            lease == null ? null : Timestamp.from(lease));
        return id;
    }

    private UUID queued(Instant createdAt) { return job("QUEUED", "S3", null, createdAt, null); }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM ingestion_jobs WHERE id = ?", String.class, id);
    }

    private String errorCode(UUID id) {
        return jdbc.queryForObject("SELECT error_code FROM ingestion_jobs WHERE id = ?", String.class, id);
    }

    private void expireLease(UUID id) {
        jdbc.update("UPDATE ingestion_jobs SET lease_expires_at = ? WHERE id = ?", Timestamp.from(Instant.now().minusSeconds(60)), id);
    }

    private List<String> listedIds(String type, String status, int limit, long offset) {
        return jobs.listAllJobs(type, status, limit, offset).stream().map(ImportJobRow::from).map(ImportJobRow::id).toList();
    }

    @Test
    @DisplayName("When two workers claim the same queued job, then exactly one wins and the oldest job is offered first")
    void whenTwoWorkersClaim_thenOneWinsAndOldestFirst() {
        UUID older = queued(Instant.now().minusSeconds(120));
        UUID newer = queued(Instant.now().minusSeconds(60));

        List<String> offered = jobs.findQueuedIds(null, 5);
        assertEquals(List.of(older.toString(), newer.toString()), offered);

        assertEquals(1, jobs.claim(older, "worker-a", Instant.now().plus(LEASE)));
        assertEquals(0, jobs.claim(older, "worker-b", Instant.now().plus(LEASE)));
        assertEquals("RUNNING", status(older));
        assertEquals("worker-a", jdbc.queryForObject("SELECT worker_id FROM ingestion_jobs WHERE id = ?", String.class, older));
        assertEquals(List.of(newer.toString()), jobs.findQueuedIds(null, 5));
    }

    @Test
    @DisplayName("When a job has local inputs, then only the instance holding them is offered the job")
    void whenInputsAreLocal_thenOnlyOwningInstanceIsOffered() {
        UUID local = job("QUEUED", "LOCAL", "host-a", Instant.now(), Instant.now().plus(LEASE));

        assertTrue(jobs.findQueuedIds("host-b", 5).isEmpty());
        assertTrue(jobs.findQueuedIds(null, 5).isEmpty());
        assertEquals(List.of(local.toString()), jobs.findQueuedIds("host-a", 5));
    }

    @Test
    @DisplayName("When another worker or an expired lease writes progress, then nothing is updated")
    void whenWorkerOrLeaseIsWrong_thenProgressIsRejected() {
        UUID id = queued(Instant.now());
        jobs.claim(id, "worker-a", Instant.now().plus(LEASE));

        assertEquals(0, jobs.recordProgress(id, "worker-b", 10, 2, 2, 0, 0, "addresses.csv", "{}", Instant.now().plus(LEASE)));
        assertEquals(1, jobs.recordProgress(id, "worker-a", 10, 2, 2, 0, 0, "addresses.csv", "{}", Instant.now().plus(LEASE)));
        assertEquals(1, jobs.renewLease(id, "worker-a", Instant.now().plus(LEASE)));
        assertEquals(0, jobs.renewLease(id, "worker-b", Instant.now().plus(LEASE)));

        expireLease(id);
        assertEquals(0, jobs.renewLease(id, "worker-a", Instant.now().plus(LEASE)));
        assertEquals(0, jobs.recordProgress(id, "worker-a", 10, 4, 4, 0, 0, "addresses.csv", "{}", Instant.now().plus(LEASE)));
        assertEquals(2, jdbc.queryForObject("SELECT processed FROM ingestion_jobs WHERE id = ?", Integer.class, id));
    }

    @Test
    @DisplayName("When a running job's lease expired, then it is interrupted and its old worker can no longer finish it")
    void whenLeaseExpired_thenJobIsInterrupted() {
        UUID id = queued(Instant.now());
        jobs.claim(id, "worker-a", Instant.now().plus(LEASE));
        UUID healthy = queued(Instant.now());
        jobs.claim(healthy, "worker-b", Instant.now().plus(LEASE));
        expireLease(id);

        assertEquals(1, jobs.interruptExpired());
        assertEquals("INTERRUPTED", status(id));
        assertEquals("WORKER_LEASE_EXPIRED", errorCode(id));
        assertEquals("RUNNING", status(healthy));
        assertEquals(0, jobs.finish(id, "worker-a", "COMPLETED", null, null));
        assertEquals(1, jobs.finish(healthy, "worker-b", "COMPLETED", null, null));
        assertEquals("COMPLETED", status(healthy));
    }

    @Test
    @DisplayName("When a local-input job was not claimed before its deadline, then it is interrupted; shared jobs wait")
    void whenQueueDeadlinePassed_thenLocalJobIsInterrupted() {
        UUID local = job("QUEUED", "LOCAL", "host-a", Instant.now(), Instant.now().minusSeconds(5));
        UUID shared = queued(Instant.now().minusSeconds(7200));

        assertEquals(1, jobs.interruptExpired());
        assertEquals("INTERRUPTED", status(local));
        assertEquals("QUEUE_DEADLINE_EXPIRED", errorCode(local));
        assertEquals("QUEUED", status(shared));
    }

    @Test
    @DisplayName("When backfill and ingestion jobs exist, then they are listed together newest first and filterable")
    void whenBothKindsExist_thenListedTogether() {
        jdbc.update("INSERT INTO image_backfill_jobs (id, county_id, status, total, processed, completed, created_at) "
            + "VALUES (7, 1, 'COMPLETED', 3, 3, 3, ?)", Timestamp.from(Instant.now().minusSeconds(300)));
        UUID failed = job("FAILED", "S3", null, Instant.now().minusSeconds(200), null);
        UUID running = job("RUNNING", "S3", null, Instant.now().minusSeconds(100), Instant.now().plus(LEASE));
        UUID retry = UUID.randomUUID();
        jdbc.update("INSERT INTO ingestion_jobs (id, job_type, dataset, status, input_storage, retry_of_job_id, attempt, created_at) "
            + "VALUES (?, 'CSV_IMPORT', 'ADDRESSES', 'QUEUED', 'S3', ?, 2, ?)", retry, failed, Timestamp.from(Instant.now()));

        List<ImportJobRow> all = jobs.listAllJobs(null, null, 10, 0).stream().map(ImportJobRow::from).toList();
        assertEquals(List.of(retry.toString(), running.toString(), failed.toString(), "7"),
            all.stream().map(ImportJobRow::id).toList());
        assertEquals(4, jobs.countAllJobs(null, null));

        ImportJobRow backfill = all.get(3);
        assertEquals("MAPILLARY_IMAGE_BACKFILL", backfill.jobType());
        assertNull(backfill.dataset());
        assertEquals(1L, backfill.countyId());
        assertEquals(3, backfill.succeeded());
        assertFalse(backfill.hasActiveRetry());
        assertNull(backfill.attempt());
        assertNotNull(backfill.createdAt());

        ImportJobRow previous = all.get(2);
        assertTrue(previous.hasActiveRetry());
        assertEquals("ADDRESSES", previous.dataset());
        assertEquals(failed.toString(), all.get(0).retryOfJobId());
        assertEquals(2, all.get(0).attempt());

        assertEquals(List.of("7"), listedIds("MAPILLARY_IMAGE_BACKFILL", null, 10, 0));
        assertEquals(List.of(running.toString()), listedIds("CSV_IMPORT", "RUNNING", 10, 0));
        assertEquals(1, jobs.countAllJobs(null, "FAILED"));
        assertEquals(List.of(failed.toString()), listedIds(null, null, 1, 2));
    }

    @Test
    @DisplayName("When checking for active jobs, then queued and running ones count")
    void whenCheckingActiveJobs_thenQueuedAndRunningCount() {
        UUID failed = job("FAILED", "S3", null, Instant.now(), null);
        assertFalse(jobs.existsByDatasetAndStatusIn(IngestionDataset.ADDRESSES, IngestionJobStatus.ACTIVE));
        assertFalse(jobs.existsByRetryOfJobIdAndStatusIn(failed, IngestionJobStatus.ACTIVE));

        UUID retry = UUID.randomUUID();
        jdbc.update("INSERT INTO ingestion_jobs (id, job_type, dataset, status, input_storage, retry_of_job_id) "
            + "VALUES (?, 'CSV_IMPORT', 'ADDRESSES', 'QUEUED', 'S3', ?)", retry, failed);
        assertTrue(jobs.existsByDatasetAndStatusIn(IngestionDataset.ADDRESSES, IngestionJobStatus.ACTIVE));
        assertTrue(jobs.existsByRetryOfJobIdAndStatusIn(failed, IngestionJobStatus.ACTIVE));
        assertFalse(jobs.existsByDatasetAndStatusIn(IngestionDataset.API_SALES, IngestionJobStatus.ACTIVE));
    }
}
