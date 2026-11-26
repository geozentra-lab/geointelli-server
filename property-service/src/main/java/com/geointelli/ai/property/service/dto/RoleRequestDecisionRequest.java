package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of an admin decision on a role request (SCRUM-204), shared by approve and reject.
 *
 * @param reason applicant-visible reason; required when rejecting, optional when approving
 */
public record RoleRequestDecisionRequest(@Size(max = 500) String reason) {
}
