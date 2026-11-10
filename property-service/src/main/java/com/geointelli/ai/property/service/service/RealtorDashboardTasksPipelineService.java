package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.RealtorTasksPipelineCardDTO;

/**
 * Builds the tasks-and-pipeline card of the realtor dashboard (SCRUM-110) for the current user.
 * Counts are taken from the existing SCRUM-79 services ({@link CrmTaskService#summary()} and
 * {@link CrmTransactionService#pipeline()}); only the bounded overdue and due-soon task lists are
 * read directly from the task repository. Access is by record ownership: only rows owned by the
 * bearer-token user are read. Persona is not checked. Nothing is modified.
 */
public interface RealtorDashboardTasksPipelineService {
    /** Size of each task list when the caller does not pass a limit. */
    int DEFAULT_LIMIT = 5;
    /** Largest accepted limit; the card is a summary, not a listing. */
    int MAX_LIMIT = 20;
    /** Length of the due-soon window when the caller does not pass one. */
    int DEFAULT_DAYS = 7;
    /** Longest accepted due-soon window, in days. */
    int MAX_DAYS = 90;

    /**
     * Builds the card: task counts by status, active tasks already overdue, active tasks due between
     * now and now plus {@code days}, and deal counts by stage. Users without data get zeros, a stage
     * map of zeros and empty lists.
     *
     * @param limit maximum items in each task list; must be between 1 and {@link #MAX_LIMIT}
     * @param days  length of the due-soon window; must be between 1 and {@link #MAX_DAYS}
     * @return the populated card with status OK
     * @throws IllegalArgumentException when limit or days is out of range (mapped to 400)
     */
    RealtorTasksPipelineCardDTO getTasksPipelineCard(int limit, int days);
}
