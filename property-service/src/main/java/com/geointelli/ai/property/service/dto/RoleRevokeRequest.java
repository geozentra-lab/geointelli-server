package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of an admin revocation of a realm role (SCRUM-204).
 *
 * @param reason why the role is removed; required, kept in the grant history
 */
public record RoleRevokeRequest(@NotBlank(message = "reason is required") @Size(max = 500) String reason) {
}
