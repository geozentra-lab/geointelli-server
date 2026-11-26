package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;

/**
 * A role request as its applicant sees it (SCRUM-204).
 *
 * @param id               request id
 * @param role             investor or lender
 * @param status           PENDING, APPROVED, REJECTED, CANCELLED or REVOKED
 * @param organizationName organization named in a lender request
 * @param note             the applicant's note
 * @param decisionReason   the admin's reason (always set on REJECTED)
 * @param createdAt        when it was requested
 * @param decidedAt        when it was decided or cancelled; null while PENDING
 */
@Schema(description = "A request for the investor or lender realm role")
public record RoleRequestDTO(Long id, String role, RoleRequestStatus status, String organizationName, String note,
                             String decisionReason, Instant createdAt, Instant decidedAt) {
}
