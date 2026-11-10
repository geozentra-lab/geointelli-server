package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.RealtorTasksPipelineCardDTO;
import com.geointelli.ai.property.service.service.RealtorDashboardTasksPipelineService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Realtor dashboard, tasks-and-pipeline card (SCRUM-110). One class per card so that the sibling
 * cards and the composed GET /api/dashboards/realtor (SCRUM-112) can be added without touching this
 * file. Scope is the bearer-token user; persona is not checked.
 */
@RestController
@RequestMapping("/api/dashboards/realtor")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Realtor dashboard", description = "Cards of the realtor dashboard, read as the current user. "
    + "Missing identity 401; invalid input 400.")
public class RealtorDashboardTasksPipelineController {
    private final RealtorDashboardTasksPipelineService service;

    @GetMapping("/tasks-pipeline")
    @Operation(summary = "Tasks and pipeline card",
        description = "Counts the current user's follow-up tasks by stored status (same numbers as GET /api/tasks/summary) "
            + "and their deals by stored stage (same numbers as GET /api/transactions/pipeline), and lists active tasks "
            + "(OPEN or IN_PROGRESS) that are overdue or due within the next days (1-90, default 7), earliest due first, "
            + "at most limit items per list (1-20, default 5). Tasks without a due date are counted but never listed. "
            + "Users without data get zeros, a stage map of zeros and empty lists with status OK. 400 when limit or days "
            + "is out of range or not a number; 401 without a bearer token.")
    public RealtorTasksPipelineCardDTO tasksPipeline(
            @RequestParam(defaultValue = "" + RealtorDashboardTasksPipelineService.DEFAULT_LIMIT) int limit,
            @RequestParam(defaultValue = "" + RealtorDashboardTasksPipelineService.DEFAULT_DAYS) int days) {
        return service.getTasksPipelineCard(limit, days);
    }
}
