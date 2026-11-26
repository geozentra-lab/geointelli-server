package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;

/**
 * A role request as the reviewing admin sees it (SCRUM-204): the applicant's request plus who they are and who
 * decided.
 *
 * @param id               request id
 * @param userId           applicant
 * @param applicantEmail   applicant e-mail from the user profile; null when unknown
 * @param role             investor or lender
 * @param status           PENDING, APPROVED, REJECTED, CANCELLED or REVOKED
 * @param organizationName organization named in a lender request
 * @param note             the applicant's note
 * @param decisionReason   the admin's reason
 * @param decidedByUserId  deciding admin (the applicant for a cancellation); null while PENDING or for an automatic
 *                         investor grant
 * @param createdAt        when it was requested
 * @param decidedAt        when it was decided; null while PENDING
 */
@Schema(description = "A role request in the admin review queue")
public record RoleRequestReviewDTO(Long id, Long userId, String applicantEmail, String role, RoleRequestStatus status,
                                   String organizationName, String note, String decisionReason, Long decidedByUserId,
                                   Instant createdAt, Instant decidedAt) {
}
