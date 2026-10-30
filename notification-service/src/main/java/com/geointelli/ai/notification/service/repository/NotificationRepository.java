package com.geointelli.ai.notification.service.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.geointelli.ai.notification.service.entity.Notification;

/**
 * Inbox API and owner-scoped reads belong to SCRUM-70; this task exposes no
 * notification REST API.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByEventId(String eventId);
}
