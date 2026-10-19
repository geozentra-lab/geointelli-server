package com.geointelli.ai.property.service.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.ImageBackfillJob;
import jakarta.persistence.LockModeType;

/** Persists backfill jobs using JPA. */
public interface ImageBackfillJobRepository extends JpaRepository<ImageBackfillJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM ImageBackfillJob j WHERE j.id = :id")
    Optional<ImageBackfillJob> findForUpdate(@Param("id") Long id);

    @Modifying
    @Query(value = """
        UPDATE image_backfill_jobs SET lease_expires_at=clock_timestamp()+interval '10 minutes'
        WHERE id=:id AND status IN ('QUEUED','RUNNING') AND lease_expires_at>clock_timestamp()
        """, nativeQuery = true)
    int renewLease(@Param("id") Long id);

    @Modifying
    @Query(value = """
        UPDATE image_backfill_jobs SET status='INTERRUPTED',error_code='WORKER_LEASE_EXPIRED',
            finished_at=clock_timestamp() WHERE status IN ('QUEUED','RUNNING')
            AND lease_expires_at<=clock_timestamp()
        """, nativeQuery = true)
    int interruptExpired();
}
