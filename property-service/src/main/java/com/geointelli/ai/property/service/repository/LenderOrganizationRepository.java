package com.geointelli.ai.property.service.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.LenderOrganization;
import jakarta.persistence.LockModeType;

public interface LenderOrganizationRepository extends JpaRepository<LenderOrganization, Long> {
    @Query("SELECT o FROM LenderOrganization o WHERE o.ownerUserId = :actor OR EXISTS "
        + "(SELECT m.id FROM LenderOrganizationMember m WHERE m.organizationId = o.id AND m.userId = :actor) ORDER BY o.id")
    List<LenderOrganization> findAllVisible(@Param("actor") Long actor);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM LenderOrganization o WHERE o.id = :id")
    Optional<LenderOrganization> lockById(@Param("id") Long id);
}

