package com.geointelli.ai.property.service.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.CrmPipelineDTO;
import com.geointelli.ai.property.service.dto.CrmTaskSummaryDTO;
import com.geointelli.ai.property.service.dto.RealtorTasksPipelineCardDTO;
import com.geointelli.ai.property.service.dto.RealtorTasksPipelineCardDTO.DueWindow;
import com.geointelli.ai.property.service.dto.RealtorTasksPipelineCardDTO.PipelineSection;
import com.geointelli.ai.property.service.dto.RealtorTasksPipelineCardDTO.TaskItem;
import com.geointelli.ai.property.service.dto.RealtorTasksPipelineCardDTO.TaskSummary;
import com.geointelli.ai.property.service.dto.RealtorTasksPipelineCardDTO.TasksSection;
import com.geointelli.ai.property.service.entity.CrmTask;
import com.geointelli.ai.property.service.entity.CrmTaskStatus;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;
import com.geointelli.ai.property.service.repository.CrmTaskRepository;
import com.geointelli.ai.property.service.service.CrmTaskService;
import com.geointelli.ai.property.service.service.CrmTransactionService;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.RealtorDashboardTasksPipelineService;

/**
 * Builds the realtor tasks-and-pipeline card. Counts are delegated to the SCRUM-79 services, which
 * already scope to the current user; the bounded overdue and due-soon lists are read from the task
 * repository with the same owner id. "Now" comes from the shared {@code crmWorkClock} bean so the
 * window and the overdue flag agree with {@code GET /api/tasks/summary}.
 */
@Service
@Transactional
public class RealtorDashboardTasksPipelineServiceImpl implements RealtorDashboardTasksPipelineService {
    /** Statuses that make a task "active": only these appear in the overdue and due-soon lists. */
    private static final Set<CrmTaskStatus> ACTIVE = EnumSet.of(CrmTaskStatus.OPEN, CrmTaskStatus.IN_PROGRESS);
    /** Stages that make a deal "open" (still in progress). */
    private static final Set<CrmTransactionStage> OPEN_STAGES = EnumSet.of(CrmTransactionStage.PROSPECT,
        CrmTransactionStage.VIEWING, CrmTransactionStage.OFFER, CrmTransactionStage.UNDER_CONTRACT);

    private final CrmTaskService taskService;
    private final CrmTransactionService transactionService;
    private final CrmTaskRepository taskRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public RealtorDashboardTasksPipelineServiceImpl(CrmTaskService taskService,
            CrmTransactionService transactionService, CrmTaskRepository taskRepository,
            CurrentUserService currentUserService, @Qualifier("crmWorkClock") Clock clock) {
        this.taskService = taskService;
        this.transactionService = transactionService;
        this.taskRepository = taskRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Override
    public RealtorTasksPipelineCardDTO getTasksPipelineCard(int limit, int days) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ", got " + limit);
        }
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and " + MAX_DAYS + ", got " + days);
        }
        Long userId = currentUserService.getCurrentUserEntity().getId();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant to = now.plus(days, ChronoUnit.DAYS);
        PageRequest firstPage = PageRequest.of(0, limit);

        // Counts: reuse the SCRUM-79 services (both read only the current user's rows).
        CrmTaskSummaryDTO summary = taskService.summary();
        CrmPipelineDTO pipeline = transactionService.pipeline();

        // Bounded lists: active tasks already overdue, and active tasks due inside the window.
        List<TaskItem> overdue = taskRepository
            .findByUserIdAndStatusInAndDueAtBeforeOrderByDueAtAsc(userId, ACTIVE, now, firstPage)
            .stream().map(this::toItem).toList();
        List<TaskItem> dueSoon = taskRepository
            .findByUserIdAndStatusInAndDueAtBetweenOrderByDueAtAsc(userId, ACTIVE, now, to, firstPage)
            .stream().map(this::toItem).toList();
        long dueSoonCount = taskRepository.countByUserIdAndStatusInAndDueAtBetween(userId, ACTIVE, now, to);

        Map<CrmTransactionStage, Long> byStage = pipeline.stages();
        long openDeals = OPEN_STAGES.stream().mapToLong(stage -> byStage.getOrDefault(stage, 0L)).sum();

        RealtorTasksPipelineCardDTO card = new RealtorTasksPipelineCardDTO();
        card.setScope(RealtorTasksPipelineCardDTO.SCOPE_CURRENT_USER);
        card.setGeneratedAt(LocalDateTime.now());
        card.setStatus(RealtorTasksPipelineCardDTO.STATUS_OK);
        card.setLimit(limit);
        card.setTasks(new TasksSection(
            new TaskSummary(summary.total(), summary.open(), summary.inProgress(), summary.completed(),
                summary.cancelled(), summary.overdue()),
            new DueWindow(days, now, to), dueSoonCount, overdue, dueSoon));
        card.setPipeline(new PipelineSection(pipeline.total(), openDeals, byStage));
        return card;
    }

    private TaskItem toItem(CrmTask t) {
        return new TaskItem(t.getId(), t.getTitle(), t.getStatus(), t.getDueAt(), t.getClientId(), t.getLeadId(),
            t.getPropertyId(), t.getTransactionId());
    }
}
