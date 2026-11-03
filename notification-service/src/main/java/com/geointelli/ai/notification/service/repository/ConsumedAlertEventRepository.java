package com.geointelli.ai.notification.service.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.notification.service.entity.ConsumedAlertEvent;

/**
 * Atomic PostgreSQL claims serialize the same event across instances without
 * aborting duplicate transactions.
 */
public interface ConsumedAlertEventRepository extends JpaRepository<ConsumedAlertEvent, String> {
    @Modifying
    @Query(value = "INSERT INTO consumed_alert_events(event_id,source,fingerprint,received_at) "
            + "VALUES(:eventId,:source,:fingerprint,:receivedAt) ON CONFLICT(event_id) DO NOTHING", nativeQuery = true)
    int claim(@Param("eventId") String eventId, @Param("source") String source,
            @Param("fingerprint") String fingerprint, @Param("receivedAt") Instant receivedAt);
}
