package com.geointelli.ai.property.service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.LenderApplication;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;

public interface LenderApplicationRepository extends JpaRepository<LenderApplication, Long> {
    Page<LenderApplication> findByOrganizationId(Long organizationId, Pageable pageable);
    Page<LenderApplication> findByOrganizationIdAndStage(Long organizationId, LenderApplicationStage stage, Pageable pageable);
    Optional<LenderApplication> findByIdAndOrganizationId(Long id, Long organizationId);
    Optional<LenderApplication> findByOrganizationIdAndSourceAndExternalId(Long organizationId, String source, String externalId);

    @Query("""
        SELECT a.stage AS stage, COUNT(a) AS count, SUM(a.requestedAmount) AS requested,
            SUM(a.approvedAmount) AS approved, SUM(a.fundedAmount) AS funded
        FROM LenderApplication a WHERE a.organizationId = :organizationId GROUP BY a.stage
        """)
    List<LenderPipelineCount> pipeline(@Param("organizationId") Long organizationId);
}

