package com.geointelli.ai.property.service.controller;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.CrmActivityDTO;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.CrmTaskCreateRequest;
import com.geointelli.ai.property.service.dto.CrmTaskDTO;
import com.geointelli.ai.property.service.dto.CrmTaskSummaryDTO;
import com.geointelli.ai.property.service.dto.CrmTaskUpdateRequest;
import com.geointelli.ai.property.service.entity.CrmTaskStatus;
import com.geointelli.ai.property.service.service.CrmTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Current-user follow-up tasks; persona does not grant access to anyone else's records. */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "CRM tasks", description = "User-owned, newest ID first; page >=0, size 1-100. Missing identity 401, "
    + "invalid input 400, missing/foreign record 404, unsupported transitions or reference conflicts 409.")
public class CrmTaskController {
    private final CrmTaskService tasks;

    @PostMapping
    @Operation(summary = "Create a follow-up task", description = "Title required (1-160), description optional (max 2000). "
        + "Optional clientId, leadId, propertyId and transactionId must be accessible and consistent. Lead/transaction "
        + "links supply their client/property where omitted. Associations are snapshots fixed at creation. "
        + "dueAt is an ISO-8601 instant with Z or a UTC offset, normalized to microseconds, years 1900-2199; past dates "
        + "are allowed for overdue tasks. Starts OPEN. Each POST creates a task (201); no reminder or email is scheduled.")
    public ResponseEntity<CrmTaskDTO> create(@RequestBody CrmTaskCreateRequest request) {
        var result = tasks.create(request);
        return ResponseEntity.created(URI.create("/api/tasks/" + result.id())).body(result);
    }

    @GetMapping
    @Operation(summary = "List my tasks", description = "Optional status: OPEN, IN_PROGRESS, COMPLETED, CANCELLED. "
        + "overdue is true only for active tasks whose dueAt is before response time. Empty data returns an empty page.")
    public CrmPageDTO<CrmTaskDTO> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) CrmTaskStatus status) {
        return tasks.list(page, size, status);
    }

    @GetMapping("/summary")
    @Operation(summary = "Count my tasks by status and overdue state", description = "Counts all my persisted tasks "
        + "in one query, independent of list pagination. Overdue excludes completed/cancelled tasks. Empty counts are zero.")
    public CrmTaskSummaryDTO summary() { return tasks.summary(); }

    @GetMapping("/{id}")
    @Operation(summary = "Read my task")
    public CrmTaskDTO get(@PathVariable Long id) { return tasks.get(id); }

    @PatchMapping("/{id}")
    @Operation(summary = "Update or complete a task", description = "PATCH title, description, dueAt or status only; "
        + "omitted fields stay unchanged, null clears description/dueAt. Title/status cannot be null. "
        + "OPEN and IN_PROGRESS can switch between each other, complete or cancel. COMPLETED/CANCELLED may reopen "
        + "only to OPEN. Completion sets completedAt; reopening clears it. Repeating the same status preserves timestamps "
        + "and adds no activity. Linked deal stages never change automatically.")
    public CrmTaskDTO update(@PathVariable Long id, @RequestBody CrmTaskUpdateRequest request) {
        return tasks.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete my task", description = "Returns 204, preserving linked records and historical activity. "
        + "Deleted task history remains in GET /api/crm/activities; its detail/history endpoints return 404.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tasks.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/activities")
    @Operation(summary = "Read this task's history")
    public CrmPageDTO<CrmActivityDTO> history(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return tasks.history(id, page, size); }
}
