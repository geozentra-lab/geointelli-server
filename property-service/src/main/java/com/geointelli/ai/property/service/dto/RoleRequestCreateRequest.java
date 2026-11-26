package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/users/me/role-requests} (SCRUM-204). Sizes match the columns of V50.
 *
 * @param role             investor or lender (realtor access comes from a license submission)
 * @param organizationName optional: the bank or mortgage company, shown to an admin when the request needs approval
 * @param note             optional message to the reviewing admin
 */
public record RoleRequestCreateRequest(
        @NotBlank(message = "role is required") @Size(max = 20) String role,
        @Size(max = 160) String organizationName,
        @Size(max = 1000) String note) {
}
