package com.geointelli.ai.notification.service.dto;

import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Exact new-match counts are separate from the bounded preview of property IDs.
 */
public record NewMatchPayloadDTO(@NotBlank @Size(max = 120) String ruleName,
                @NotNull @Pattern(regexp = "[a-fA-F0-9]{64}") String definitionHash,
                @NotNull @Positive Long matchedCount, @NotNull @Positive Long newMatchCount,
                @NotNull @Size(min = 1, max = 100) List<@NotNull @Positive Long> propertyIds,
                @NotNull Boolean previewTruncated, @NotNull Instant observationStartedAt,
                @NotNull Instant observationFinishedAt) {
}
