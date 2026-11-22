package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.AdminCoverageHealthDTO;
import com.geointelli.ai.property.service.service.AdminCoverageHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin coverage and provider-health dashboard (SCRUM-92). Requires the admin role, like every /api/admin route. */
@RestController
@RequestMapping("/api/admin/coverage-health")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin coverage and provider health", description = "Data-quality counts, source coverage and "
        + "provider health from recorded evidence. Missing token 401; non-admin 403. Read-only.")
public class AdminCoverageHealthController {

    private final AdminCoverageHealthService adminCoverageHealthService;

    /**
     * Returns the dashboard.
     *
     * @return 200 with quality, coverage and provider health; unreadable or empty blocks are UNAVAILABLE with a reason
     */
    @GetMapping
    @Operation(summary = "Coverage and provider-health dashboard", description = "No parameters. Answers at once: "
            + "quality and coverage are full-table counts kept in memory and recalculated in the background once older "
            + "than admin.coverage-health.refresh-seconds (600 by default); until the first calculation ends, both are "
            + "UNAVAILABLE with the reason 'Being calculated in the background; try again in a few minutes'. A refresh "
            + "that fails keeps the last good block with its time. quality reuses the data-quality scan summary "
            + "(generatedAt is when its counts were calculated). coverage counts, for each source, the properties with "
            + "at least one recorded row (countedAt is when); UNAVAILABLE when no property is recorded; a percentage is "
            + "100.00 only when every property is covered and 0.00 only when none is. "
            + "providers: MIAMI_DADE_API and CSV_IMPORT from ingestion_jobs, MAPILLARY from image_backfill_jobs; "
            + "HEALTHY if the latest finished job is COMPLETED, DEGRADED if PARTIAL, FAILED or INTERRUPTED, "
            + "UNAVAILABLE if no job has finished. No provider is called.")
    public AdminCoverageHealthDTO getDashboard() {
        return adminCoverageHealthService.getDashboard();
    }
}
