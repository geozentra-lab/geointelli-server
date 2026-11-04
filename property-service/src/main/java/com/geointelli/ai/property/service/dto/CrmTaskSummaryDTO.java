package com.geointelli.ai.property.service.dto;

import java.time.Instant;

/** Counts are calculated in one owned query; overdue includes only active tasks. */
public record CrmTaskSummaryDTO(long total, long open, long inProgress, long completed,
        long cancelled, long overdue, Instant asOf) {
}
