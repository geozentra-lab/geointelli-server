package com.geointelli.ai.property.service.repository;

import java.time.Instant;
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

    /** Applications still approved (APPROVED or FUNDED) whose approvedAt is in [from, to), by loan type. */
    @Query("""
        SELECT a.loanType AS loanType, COUNT(a) AS count, SUM(a.approvedAmount) AS total
        FROM LenderApplication a
        WHERE a.organizationId = :organizationId AND a.stage IN :stages
            AND a.approvedAt >= :from AND a.approvedAt < :to
        GROUP BY a.loanType
        """)
    List<LenderLoanTypeTotal> approvedByLoanType(@Param("organizationId") Long organizationId,
                                                 @Param("stages") List<LenderApplicationStage> stages, @Param("from") Instant from, @Param("to") Instant to);

    /** FUNDED applications whose fundedAt is in [from, to), by loan type. */
    @Query("""
        SELECT a.loanType AS loanType, COUNT(a) AS count, SUM(a.fundedAmount) AS total
        FROM LenderApplication a
        WHERE a.organizationId = :organizationId AND a.stage = :stage
            AND a.fundedAt >= :from AND a.fundedAt < :to
        GROUP BY a.loanType
        """)
    List<LenderLoanTypeTotal> fundedByLoanType(@Param("organizationId") Long organizationId,
                                               @Param("stage") LenderApplicationStage stage, @Param("from") Instant from, @Param("to") Instant to);
}

