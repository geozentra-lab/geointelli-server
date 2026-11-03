package com.geointelli.ai.property.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A note is the only activity content a caller can submit directly. */
public record CrmNoteRequest(@NotBlank @Size(max = 2000) String description) {
}
