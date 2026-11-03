package com.geointelli.ai.property.service.dto;

/** Internal create outcome used to distinguish a new lead from an idempotent replay. */
public record CrmLeadSubmissionDTO(CrmLeadDTO lead, boolean created) {
}
