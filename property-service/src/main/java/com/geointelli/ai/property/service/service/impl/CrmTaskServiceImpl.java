package com.geointelli.ai.property.service.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.CrmActivityDTO;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.CrmTaskCreateRequest;
import com.geointelli.ai.property.service.dto.CrmTaskDTO;
import com.geointelli.ai.property.service.dto.CrmTaskSummaryDTO;
import com.geointelli.ai.property.service.dto.CrmTaskUpdateRequest;
import com.geointelli.ai.property.service.entity.CrmActivityType;
import com.geointelli.ai.property.service.entity.CrmSubjectType;
import com.geointelli.ai.property.service.entity.CrmTask;
import com.geointelli.ai.property.service.entity.CrmTaskStatus;
import com.geointelli.ai.property.service.mapper.CrmWorkMapper;
import com.geointelli.ai.property.service.repository.CrmActivityRepository;
import com.geointelli.ai.property.service.repository.CrmTaskRepository;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmTaskService;
import com.geointelli.ai.property.service.service.CrmValidationService;
import com.geointelli.ai.property.service.service.CrmWorkReferenceService;
import com.geointelli.ai.property.service.service.CurrentUserService;

/** Persists owner-scoped tasks and real history atomically, without a background scheduler. */
@Service
@Transactional
public class CrmTaskServiceImpl implements CrmTaskService {
    private final CrmTaskRepository tasks;
    private final CurrentUserService currentUser;
    private final CrmValidationService validation;
    private final CrmWorkReferenceService references;
    private final CrmActivityService activity;
    private final CrmActivityRepository history;
    private final CrmWorkMapper mapper;
    private final Clock clock;

    public CrmTaskServiceImpl(CrmTaskRepository tasks, CurrentUserService currentUser, CrmValidationService validation,
            CrmWorkReferenceService references, CrmActivityService activity, CrmActivityRepository history,
            CrmWorkMapper mapper, @Qualifier("crmWorkClock") Clock clock) {
        this.tasks = tasks;
        this.currentUser = currentUser;
        this.validation = validation;
        this.references = references;
        this.activity = activity;
        this.history = history;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public CrmTaskDTO create(CrmTaskCreateRequest request) {
        Long owner = currentUser.getCurrentUserEntity().getId();
        if (request == null) throw new IllegalArgumentException("Request body is required");
        CrmTaskCreateRequest value = normalized(request);
        validation.validate(value);
        var links = references.resolve(owner, value.clientId(), value.leadId(), value.propertyId(), value.transactionId());
        CrmTask task = new CrmTask();
        task.setUserId(owner);
        task.setClientId(links.clientId());
        task.setLeadId(links.leadId());
        task.setPropertyId(links.propertyId());
        task.setTransactionId(links.transactionId());
        task.setTitle(value.title());
        task.setDescription(value.description());
        task.setDueAt(value.dueAt());
        tasks.saveAndFlush(task);
        record(task, CrmActivityType.TASK_CREATED, "Task created in status OPEN");
        return mapper.toDTO(task, now());
    }

    @Override
    public CrmPageDTO<CrmTaskDTO> list(int page, int size, CrmTaskStatus status) {
        Long owner = currentUser.getCurrentUserEntity().getId();
        var pageable = validation.page(page, size);
        var result = status == null ? tasks.findByUserId(owner, pageable) : tasks.findByUserIdAndStatus(owner, status, pageable);
        Instant observedAt = now();
        return new CrmPageDTO<>(result.map(task -> mapper.toDTO(task, observedAt)).getContent(),
            page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public CrmTaskDTO get(Long id) { return mapper.toDTO(owned(id, false), now()); }

    @Override
    public CrmTaskDTO update(Long id, CrmTaskUpdateRequest request) {
        CrmTask task = owned(id, true);
        if (request == null) throw new IllegalArgumentException("Request body is required");
        var value = normalized(new CrmTaskCreateRequest(
            request.isTitleProvided() ? request.getTitle() : task.getTitle(),
            request.isDescriptionProvided() ? request.getDescription() : task.getDescription(),
            request.isDueAtProvided() ? request.getDueAt() : task.getDueAt(),
            task.getClientId(), task.getLeadId(), task.getPropertyId(), task.getTransactionId()));
        validation.validate(value);
        CrmTaskStatus next = request.isStatusProvided() ? request.getStatus() : task.getStatus();
        if (next == null) throw new IllegalArgumentException("status cannot be null");
        if (!task.getStatus().canChangeTo(next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unsupported task transition: " + task.getStatus() + " -> " + next);
        }
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(task.getTitle(), value.title())) changed.add("title");
        if (!Objects.equals(task.getDescription(), value.description())) changed.add("description");
        if (!Objects.equals(task.getDueAt(), value.dueAt())) changed.add("dueAt");
        boolean statusChanged = task.getStatus() != next;
        String transition = "Status " + task.getStatus() + " -> " + next;
        if (statusChanged) changed.add("status");
        if (!changed.isEmpty()) {
            task.setTitle(value.title());
            task.setDescription(value.description());
            task.setDueAt(value.dueAt());
            if (statusChanged) task.setCompletedAt(next == CrmTaskStatus.COMPLETED ? now() : null);
            task.setStatus(next);
            tasks.saveAndFlush(task);
            record(task, statusChanged ? CrmActivityType.TASK_STATUS_CHANGED : CrmActivityType.TASK_UPDATED,
                (statusChanged ? transition + "; " : "") + "Changed: " + String.join(", ", changed));
        }
        return mapper.toDTO(task, now());
    }

    @Override
    public void delete(Long id) {
        CrmTask task = owned(id, true);
        record(task, CrmActivityType.TASK_DELETED, "Task deleted");
        tasks.delete(task);
        tasks.flush();
    }

    @Override
    public CrmTaskSummaryDTO summary() {
        Long owner = currentUser.getCurrentUserEntity().getId();
        Instant observedAt = now();
        var counts = tasks.summary(owner, observedAt, CrmTaskStatus.OPEN, CrmTaskStatus.IN_PROGRESS,
            CrmTaskStatus.COMPLETED, CrmTaskStatus.CANCELLED);
        return new CrmTaskSummaryDTO(counts.getTotal(), counts.getOpen(), counts.getInProgress(),
            counts.getCompleted(), counts.getCancelled(), counts.getOverdue(), observedAt);
    }

    @Override
    public CrmPageDTO<CrmActivityDTO> history(Long id, int page, int size) {
        CrmTask task = owned(id, false);
        var result = history.findByUserIdAndSubjectTypeAndSubjectId(task.getUserId(), CrmSubjectType.TASK, id,
            validation.page(page, size));
        return new CrmPageDTO<>(result.map(mapper::toDTO).getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    private CrmTask owned(Long id, boolean lock) {
        validation.requirePositive(id, "taskId");
        Long owner = currentUser.getCurrentUserEntity().getId();
        return (lock ? tasks.findOwnedForUpdate(id, owner) : tasks.findByIdAndUserId(id, owner))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private CrmTaskCreateRequest normalized(CrmTaskCreateRequest value) {
        Instant due = value.dueAt();
        if (due != null && (due.isBefore(Instant.parse("1900-01-01T00:00:00Z"))
                || !due.isBefore(Instant.parse("2200-01-01T00:00:00Z")))) {
            throw new IllegalArgumentException("dueAt must be within years 1900 to 2199");
        }
        return new CrmTaskCreateRequest(validation.normalize(value.title()), validation.normalize(value.description()),
            due == null ? null : due.truncatedTo(ChronoUnit.MICROS), value.clientId(), value.leadId(), value.propertyId(), value.transactionId());
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    private void record(CrmTask task, CrmActivityType type, String description) {
        activity.record(task.getUserId(), task.getClientId(), CrmSubjectType.TASK, task.getId(), type, description);
    }
}
