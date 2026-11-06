package com.geointelli.ai.property.service.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.ContactRequestOutbox;

/** SKIP LOCKED lets instances publish distinct rows; broker confirms precede committing SENT. */
public interface ContactRequestOutboxRepository extends JpaRepository<ContactRequestOutbox, Long> {
    Optional<ContactRequestOutbox> findByRequestId(Long requestId);

    @Query(value = """
        SELECT * FROM contact_request_outbox
        WHERE status = 'PENDING' AND next_attempt_at <= :now
        ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<ContactRequestOutbox> lockNext(@Param("now") Instant now);
}

