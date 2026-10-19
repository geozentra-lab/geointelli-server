package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.ImageBackfillClaim;

/** Uses PostgreSQL row claiming while persisting claim records through JPA. */
public interface ImageBackfillClaimRepository extends JpaRepository<ImageBackfillClaim, Long> {
    @Query(value = """
        SELECT p.id FROM properties p
        LEFT JOIN image_backfill_claims c ON c.property_id=p.id
        WHERE p.county_id=:countyId AND p.id>:afterId
          AND NOT EXISTS (SELECT 1 FROM property_images i WHERE i.property_id=p.id)
          AND (c.property_id IS NULL OR c.status<>'CLAIMED' OR c.expires_at<=clock_timestamp())
        ORDER BY p.id LIMIT :batchSize FOR UPDATE OF p SKIP LOCKED
        """, nativeQuery = true)
    List<Long> findAvailable(@Param("countyId") Long countyId, @Param("afterId") long afterId,
        @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = """
        INSERT INTO image_backfill_claims(property_id,job_id,status,expires_at)
        VALUES (:propertyId,:jobId,'CLAIMED',clock_timestamp()+interval '10 minutes')
        ON CONFLICT (property_id) DO UPDATE SET job_id=EXCLUDED.job_id,
            status='CLAIMED',expires_at=EXCLUDED.expires_at
        WHERE image_backfill_claims.status<>'CLAIMED' OR image_backfill_claims.expires_at<=clock_timestamp()
        """, nativeQuery = true)
    int claim(@Param("propertyId") Long propertyId, @Param("jobId") Long jobId);

    @Modifying
    @Query(value = """
        WITH available AS (
            SELECT property_id FROM image_backfill_claims
            WHERE job_id=:jobId AND status='CLAIMED' AND expires_at>clock_timestamp()
            FOR UPDATE SKIP LOCKED
        )
        UPDATE image_backfill_claims c SET expires_at=clock_timestamp()+interval '10 minutes'
        FROM available a WHERE c.property_id=a.property_id
        """, nativeQuery = true)
    int renew(@Param("jobId") Long jobId);

    @Query(value = """
        SELECT EXISTS(SELECT 1 FROM image_backfill_claims c JOIN image_backfill_jobs j ON j.id=c.job_id
        WHERE c.property_id=:propertyId AND c.job_id=:jobId AND c.status='CLAIMED' AND c.expires_at>clock_timestamp()
          AND j.status IN ('QUEUED','RUNNING') AND j.lease_expires_at>clock_timestamp())
        """, nativeQuery = true)
    boolean owns(@Param("jobId") Long jobId, @Param("propertyId") Long propertyId);

    @Query(value = """
        SELECT c.* FROM image_backfill_claims c JOIN image_backfill_jobs j ON j.id=c.job_id
        WHERE c.property_id=:propertyId AND c.job_id=:jobId
          AND c.status='CLAIMED' AND c.expires_at>clock_timestamp()
          AND j.status IN ('QUEUED','RUNNING') AND j.lease_expires_at>clock_timestamp() FOR UPDATE OF c
        """, nativeQuery = true)
    Optional<ImageBackfillClaim> findOwnedForUpdate(@Param("jobId") Long jobId, @Param("propertyId") Long propertyId);

    @Modifying
    @Query(value = """
        UPDATE image_backfill_claims SET status='RELEASED',expires_at=clock_timestamp()
        WHERE job_id=:jobId AND property_id=:propertyId AND status='CLAIMED'
        """, nativeQuery = true)
    int complete(@Param("jobId") Long jobId, @Param("propertyId") Long propertyId);

    @Modifying
    @Query(value = """
        UPDATE image_backfill_claims SET status='RELEASED',expires_at=clock_timestamp()
        WHERE job_id=:jobId AND status='CLAIMED'
        """, nativeQuery = true)
    int release(@Param("jobId") Long jobId);
}
