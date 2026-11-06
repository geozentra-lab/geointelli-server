package com.geointelli.ai.property.service.dto;

/** An identical idempotency-key replay returns the same request with created=false. */
public record ContactRequestSubmissionDTO(ContactResponseDTO request, boolean created) {}

