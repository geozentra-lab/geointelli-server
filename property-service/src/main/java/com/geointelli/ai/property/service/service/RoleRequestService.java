package com.geointelli.ai.property.service.service;

import java.util.List;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.RoleRequestCreateRequest;
import com.geointelli.ai.property.service.dto.RoleRequestDTO;
import com.geointelli.ai.property.service.dto.RoleRequestReviewDTO;
import com.geointelli.ai.property.service.entity.RoleRequestStatus;

/**
 * Requests for the investor and lender realm roles from the onboarding page (SCRUM-204). The applicant is
 * always the user behind the bearer token, the reviewer always the admin behind it. A request waits for an admin
 * approval, unless its role is listed in onboarding.self-service-roles (empty by default): then it is granted in
 * Keycloak at once. Realtor access comes from an approved license, never from a request.
 */
public interface RoleRequestService {

    /**
     * Creates a request of the current user.
     *
     * @param request role (investor or lender), optional organization name and note
     * @return the request: APPROVED for a self-service role once Keycloak accepted it, otherwise PENDING (also when
     *         Keycloak could not be reached: an admin can approve it later)
     * @throws IllegalArgumentException 400: role not investor or lender (realtor uses the license submission)
     * @throws org.springframework.web.server.ResponseStatusException 409: the user already has the role's features, or
     *         a request for that role is already pending
     */
    RoleRequestDTO create(RoleRequestCreateRequest request);

    /** The current user's requests, newest first. */
    List<RoleRequestDTO> listMine();

    /**
     * One request of the current user.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 when missing or another user's
     */
    RoleRequestDTO getMine(Long id);

    /**
     * Withdraws a pending request of the current user.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 when missing or another user's; 409 when it
     *         is no longer pending
     */
    RoleRequestDTO cancelMine(Long id);

    /**
     * Admin review queue, newest first.
     *
     * @param status optional status filter
     * @param role   optional role filter (investor or lender)
     * @param page   page number from 0
     * @param size   page size, 1 to 100
     */
    PageResponse<RoleRequestReviewDTO> list(RoleRequestStatus status, String role, int page, int size);

    /** One request for an admin; 404 when missing. */
    RoleRequestReviewDTO get(Long id);

    /**
     * Approves a pending request: grants the role in Keycloak, records it, then marks the request APPROVED.
     *
     * @param reason optional reason shown to the applicant
     * @throws org.springframework.web.server.ResponseStatusException 404 missing; 409 not pending
     * @throws com.geointelli.ai.property.service.exception.KeycloakRoleException 503 when Keycloak refused or is
     *         unreachable: the request stays PENDING
     */
    RoleRequestReviewDTO approve(Long id, String reason);

    /**
     * Rejects a pending request; Keycloak is not called.
     *
     * @param reason required, shown to the applicant
     * @throws IllegalArgumentException 400 when the reason is missing
     * @throws org.springframework.web.server.ResponseStatusException 404 missing; 409 not pending
     */
    RoleRequestReviewDTO reject(Long id, String reason);
}
