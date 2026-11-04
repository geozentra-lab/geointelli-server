package com.geointelli.ai.property.service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.geointelli.ai.property.service.entity.CrmActivity;
import com.geointelli.ai.property.service.entity.CrmSubjectType;

/** Append-only history queries always include the authenticated owner. */
public interface CrmActivityRepository extends JpaRepository<CrmActivity, Long> {
    Page<CrmActivity> findByUserId(Long userId, Pageable pageable);
    Page<CrmActivity> findByUserIdAndClientId(Long userId, Long clientId, Pageable pageable);
    Page<CrmActivity> findByUserIdAndSubjectTypeAndSubjectId(Long userId,
        CrmSubjectType subjectType, Long subjectId, Pageable pageable);
}
