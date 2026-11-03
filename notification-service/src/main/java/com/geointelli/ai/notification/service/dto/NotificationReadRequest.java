package com.geointelli.ai.notification.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** Marks a notification read. Marking unread is outside the supported inbox workflow. */
public record NotificationReadRequest(
        @NotNull(message = "read is required")
        @AssertTrue(message = "read must be true")
        @Schema(description = "Must be true. Repeating this request is safe.", example = "true") Boolean read) {}
