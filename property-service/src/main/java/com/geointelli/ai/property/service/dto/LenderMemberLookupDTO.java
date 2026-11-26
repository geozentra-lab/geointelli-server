package com.geointelli.ai.property.service.dto;

/**
 * Account found by email for an organization owner who adds members: {@code userId} goes into
 * POST /api/lender/organizations/{organizationId}/members. {@code member} is true when the account is already the
 * owner or a member of that organization.
 */
public record LenderMemberLookupDTO(Long userId, String email, String firstName, String lastName, boolean member) {}
