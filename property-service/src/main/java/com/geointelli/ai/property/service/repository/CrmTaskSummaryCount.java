package com.geointelli.ai.property.service.repository;

/** One row containing counts from the same owned task query. */
public interface CrmTaskSummaryCount {
    long getTotal();
    long getOpen();
    long getInProgress();
    long getCompleted();
    long getCancelled();
    long getOverdue();
}
