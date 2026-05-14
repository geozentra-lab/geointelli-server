package com.geointelli.ai.property.service.advice;

import java.time.Instant;

import lombok.Builder;

@Builder
public record ApiError(
    int status,
    String error,
    String message,
    Instant timestamp
) {}