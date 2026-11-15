package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.geointelli.ai.property.service.entity.CrmTaskStatus;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Tasks-and-pipeline card of the realtor dashboard (SCRUM-110): the current user's follow-up tasks
 * (counts by stored status, overdue and due-soon lists inside a documented window) and their deal
 * pipeline (counts by stored stage), all from SCRUM-79 records. Scope is the bearer-token user who
 * owns the records; persona is not checked.
 */
@Data
public class RealtorTasksPipelineCardDTO {
    /** Value of {@link #scope} for every dashboard card that is read as the current user. */
    public static final String SCOPE_CURRENT_USER = "CURRENT_USER";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";

    @Schema(description = "Whose data the card shows; always CURRENT_USER", example = SCOPE_CURRENT_USER)
    private String scope;

    @Schema(description = "Freshness: when the card was built (server time)")
    private LocalDateTime generatedAt;

    @Schema(description = "OK when the data loaded; ERROR when the dashboard could not load this card", example = STATUS_OK)
    private String status;

    @Schema(description = "Why the card failed; null when status is OK")
    private String error;

    @Schema(description = "Maximum number of items in each task list (overdue, dueSoon)", example = "5")
    private int limit;

    @Schema(description = "The user's follow-up tasks")
    private TasksSection tasks;

    @Schema(description = "The user's property deals, counted by stored stage")
    private PipelineSection pipeline;

    /** Task counts, the applied due window and the two bounded lists. */
    public record TasksSection(
        @Schema(description = "Counts of all persisted tasks, by status") TaskSummary summary,
        @Schema(description = "The date range used for dueSoon") DueWindow window,
        @Schema(description = "Active tasks due inside the window, before applying limit", example = "3") long dueSoonCount,
        @Schema(description = "Active tasks past their due date, earliest due first, at most limit items") List<TaskItem> overdue,
        @Schema(description = "Active tasks due inside the window, earliest due first, at most limit items") List<TaskItem> dueSoon) {
    }

    /** Same numbers as GET /api/tasks/summary; overdue counts only active tasks (OPEN or IN_PROGRESS). */
    public record TaskSummary(
        @Schema(example = "8") long total,
        @Schema(example = "4") long open,
        @Schema(example = "1") long inProgress,
        @Schema(example = "2") long completed,
        @Schema(example = "1") long cancelled,
        @Schema(description = "Active tasks whose dueAt is before now", example = "2") long overdue) {
    }

    /** The due-soon window: from now (inclusive) to now plus days (inclusive), in UTC. */
    public record DueWindow(
        @Schema(description = "Length of the window in days, as requested", example = "7") int days,
        @Schema(description = "Start of the window: the moment the card was built") Instant from,
        @Schema(description = "End of the window: from plus days") Instant to) {
    }

    /** One task, summarized for display; description omitted. Links to GET /api/tasks/{id}. */
    public record TaskItem(
        Long id,
        String title,
        @Schema(description = "Stored status; always OPEN or IN_PROGRESS in these lists") CrmTaskStatus status,
        @Schema(description = "Due moment in UTC") Instant dueAt,
        @Schema(description = "Linked client, or null") Long clientId,
        @Schema(description = "Linked lead, or null") Long leadId,
        @Schema(description = "Linked property, or null") Long propertyId,
        @Schema(description = "Linked deal, or null") Long transactionId) {
    }

    /** Deal counts by stage, same numbers as GET /api/transactions/pipeline. Each deal is in exactly one stage. */
    public record PipelineSection(
        @Schema(description = "All persisted deals of the user (sum of byStage)", example = "6") long total,
        @Schema(description = "Deals still in progress: PROSPECT + VIEWING + OFFER + UNDER_CONTRACT", example = "4") long open,
        @Schema(description = "Count per stage; every stage is present, 0 when empty",
            example = "{\"PROSPECT\": 2, \"VIEWING\": 1, \"OFFER\": 0, \"UNDER_CONTRACT\": 1, \"CLOSED\": 1, \"CANCELLED\": 1}")
        Map<CrmTransactionStage, Long> byStage) {
    }
}
