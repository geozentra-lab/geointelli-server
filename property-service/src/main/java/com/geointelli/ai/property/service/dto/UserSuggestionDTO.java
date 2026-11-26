package com.geointelli.ai.property.service.dto;

/**
 * Account suggested while an admin types (GET /api/admin/users/search): {@code id} goes into
 * /api/admin/users/{userId}/role-grants and the revoke route; GET /api/admin/users?email= gives the full account.
 */
public record UserSuggestionDTO(Long id, String email, String firstName, String lastName) {}
