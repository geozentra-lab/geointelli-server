package com.geointelli.ai.property.service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.AlertMonitoringStatusDTO;
import com.geointelli.ai.property.service.service.AlertMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/** Owner-only visibility and manual scheduling for background alert evaluation. */
@RestController
@RequestMapping("/api/alerts")
@SecurityRequirement(name="bearerAuth")
public class AlertMonitoringController {
    private final AlertMonitoringService service;

    public AlertMonitoringController(AlertMonitoringService service) { this.service=service; }

    @GetMapping("/{id}/monitoring")
    @Operation(summary="Get alert monitoring progress", description="The first complete scan establishes a silent baseline. NEW_MATCH scans all matching IDs; daily/weekly intervals run from completion in UTC. Other event sources currently report BLOCKED.")
    public AlertMonitoringStatusDTO status(@PathVariable Long id) { return service.status(id); }
    
    @PostMapping("/{id}/evaluate")
    @Operation(summary="Schedule an alert evaluation now", description="Returns 202 without waiting for the scan. Requires enabled monitoring and an unpaused NEW_MATCH rule. No request body. Events include an exact new-match count and at most 100 preview IDs.")
    public ResponseEntity<Void> evaluate(@PathVariable Long id) { service.evaluate(id); return ResponseEntity.accepted().build(); }
}
