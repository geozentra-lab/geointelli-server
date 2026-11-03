package com.geointelli.ai.notification.service.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.notification.service.entity.EmailDelivery;

/** Provider acceptance is durable; expired in-flight attempts are never automatically resent. */
public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery,Long> {
    Optional<EmailDelivery> findFirstByRecipientKeycloakIdAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
        String recipientKeycloakId,String status,Instant now);

    @Modifying
    @Query("UPDATE EmailDelivery d SET d.status='UNKNOWN',d.lastErrorCode='WORKER_LEASE_EXPIRED' WHERE d.status='SENDING' AND d.leaseExpiresAt<=:now")
    int recoverExpired(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE EmailDelivery d SET d.status='SUPPRESSED',d.lastErrorCode='PREFERENCES_CHANGED' WHERE d.recipientKeycloakId=:recipient AND d.status='RETRY'")
    int suppressRetries(@Param("recipient") String recipient);
}
