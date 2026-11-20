package com.geointelli.ai.property.service.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.DataQualityIssueReview;

/**
 * Review log of data-quality findings (SCRUM-190). Rows are only inserted; the newest row of a reference is the
 * current state of the finding. The existence checks read the source tables but never change them.
 */
public interface DataQualityIssueReviewRepository extends JpaRepository<DataQualityIssueReview, Long> {

    /** Newest review of one finding: its current state. Empty when the finding was never reviewed (OPEN). */
    Optional<DataQualityIssueReview> findFirstByIssueReferenceOrderByIdDesc(String issueReference);

    /** Full history of one finding, oldest first. */
    List<DataQualityIssueReview> findByIssueReferenceOrderByIdAsc(String issueReference);

    /** Newest review of each of the given findings, for decorating one page of the scanner report in one query. */
    @Query(value = """
            SELECT DISTINCT ON (r.issue_reference) r.*
            FROM data_quality_issue_reviews r
            WHERE r.issue_reference IN (:references)
            ORDER BY r.issue_reference, r.id DESC
            """, nativeQuery = true)
    List<DataQualityIssueReview> findLatestByIssueReferenceIn(@Param("references") Collection<String> references);

    /** The property, if it exists. */
    @Query(value = "SELECT p.id AS \"propertyId\" FROM properties p WHERE p.id = :id", nativeQuery = true)
    Optional<DataQualityEntityRow> findProperty(@Param("id") Long id);

    /** The sale, if it exists, with its property. */
    @Query(value = "SELECT s.property_id AS \"propertyId\" FROM sales s WHERE s.id = :id", nativeQuery = true)
    Optional<DataQualityEntityRow> findSale(@Param("id") Long id);

    /** The assessment, if it exists, with its property. */
    @Query(value = "SELECT a.property_id AS \"propertyId\" FROM assessments a WHERE a.id = :id", nativeQuery = true)
    Optional<DataQualityEntityRow> findAssessment(@Param("id") Long id);

    /**
     * Takes a transaction-scoped advisory lock named after the reference, so two reviews of the same finding
     * run one after the other and the second one sees the first one's row. Released when the transaction ends.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:reference))) l", nativeQuery = true)
    Integer lockReference(@Param("reference") String reference);
}
