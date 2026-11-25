package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import com.geointelli.ai.property.service.entity.RoleGrantAction;
import com.geointelli.ai.property.service.entity.RoleGrantSource;

/**
 * One grant or revocation of a Keycloak realm role done by GeoZentra (SCRUM-204).
 *
 * @param id              event id
 * @param userId          user whose role changed
 * @param role            realm role: investor, realtor or lender
 * @param action          GRANT or REVOKE
 * @param source          what decided it
 * @param sourceId        id of the deciding record (role request or realtor license); null when there is none
 * @param reason          why
 * @param decidedByUserId admin who decided; null for automatic decisions
 * @param createdAt       when GeoZentra changed the role in Keycloak
 */
@Schema(description = "One realm role grant or revocation done by GeoZentra")
public record RoleGrantEventDTO(Long id, Long userId, String role, RoleGrantAction action, RoleGrantSource source,
                                Long sourceId, String reason, Long decidedByUserId, Instant createdAt) {
}
