package com.geointelli.ai.property.service.dto;

/** Existing local user to grant organization access to. Only the organization owner may grant access. */
public record LenderMemberRequest(Long userId) {}

