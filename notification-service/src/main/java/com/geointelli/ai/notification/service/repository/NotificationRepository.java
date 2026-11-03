package com.geointelli.ai.notification.service.repository;

import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.notification.service.entity.Notification;

/**
 * Stores consumed notifications and provides indexed recipient-scoped inbox
 * operations.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByEventId(String eventId);

    Page<Notification> findByRecipientKeycloakId(String recipientKeycloakId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientKeycloakId(Long id, String recipientKeycloakId);

    /**
     * Sets the first read time only, including when multiple service instances
     * update the same item.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :readAt WHERE n.id = :id "
            + "AND n.recipientKeycloakId = :recipient AND n.readAt IS NULL")
    int markUnreadAsRead(@Param("id") Long id, @Param("recipient") String recipient,
            @Param("readAt") Instant readAt);
}
