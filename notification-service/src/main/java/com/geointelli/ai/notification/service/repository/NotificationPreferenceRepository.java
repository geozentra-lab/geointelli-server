package com.geointelli.ai.notification.service.repository;

import java.time.Instant;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.notification.service.entity.NotificationPreference;

/** Serializes changes and dispatch decisions per recipient, not for the entire service. */
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference,String> {
    @Modifying
    @Query(value="INSERT INTO notification_preferences(recipient_keycloak_id,updated_at) VALUES(:recipient,:now) ON CONFLICT DO NOTHING",nativeQuery=true)
    int ensureDefault(@Param("recipient") String recipient,@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM NotificationPreference p WHERE p.recipientKeycloakId=:recipient")
    Optional<NotificationPreference> findForUpdate(@Param("recipient") String recipient);

    @Query(value="SELECT p.* FROM notification_preferences p WHERE p.email_enabled AND "
        + "((p.next_email_at<=:now AND EXISTS(SELECT 1 FROM notification_email_queue q JOIN notifications n ON n.id=q.notification_id "
        + "WHERE q.recipient_keycloak_id=p.recipient_keycloak_id AND q.status='PENDING' AND "
        + "((n.event_type='NEW_MATCH' AND p.new_match_enabled) OR (n.event_type='CONTACT_REQUEST' AND p.contact_request_enabled)))) "
        + "OR EXISTS(SELECT 1 FROM notification_email_deliveries d WHERE d.recipient_keycloak_id=p.recipient_keycloak_id AND d.status='RETRY' AND d.next_attempt_at<=:now)) "
        + "ORDER BY p.next_email_at,p.recipient_keycloak_id LIMIT 1 FOR UPDATE OF p SKIP LOCKED",nativeQuery=true)
    Optional<NotificationPreference> lockNextDue(@Param("now") Instant now);
}
