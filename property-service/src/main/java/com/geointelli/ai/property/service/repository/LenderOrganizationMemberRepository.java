package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.geointelli.ai.property.service.entity.LenderOrganizationMember;

public interface LenderOrganizationMemberRepository extends JpaRepository<LenderOrganizationMember, Long> {
    boolean existsByOrganizationIdAndUserId(Long organizationId, Long userId);
    Optional<LenderOrganizationMember> findByOrganizationIdAndUserId(Long organizationId, Long userId);
    List<LenderOrganizationMember> findByOrganizationIdOrderByUserId(Long organizationId);
    List<LenderOrganizationMember> findByUserIdOrderByOrganizationId(Long userId);
}

