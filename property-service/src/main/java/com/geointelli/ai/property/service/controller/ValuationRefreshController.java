package com.geointelli.ai.property.service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.ValuationRefreshResponse;
import com.geointelli.ai.property.service.service.ValuationRefreshService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/** Administrator operations protected by the existing /api/admin role rule. */
@RestController
@RequestMapping("/api/admin/valuation-refresh-requests")
@RequiredArgsConstructor
public class ValuationRefreshController {
    private final ValuationRefreshService refresh;

    /** Returns processing diagnostics; Python workers poll the table independently. */
    @GetMapping
    @Operation(summary = "List valuation refresh requests", description = "Requires Keycloak realm role admin. Status is optional and uppercase.")
    public PageResponse<ValuationRefreshResponse> list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return refresh.list(status, page, size);
    }

    /** Reads one request, including its eventual publication run ID. */
    @GetMapping("/{id}")
    public ValuationRefreshResponse get(@PathVariable UUID id) {
        return refresh.get(id);
    }

    /** Cancels pending or failed requests; rejects leased/completed work with 409. */
    @PostMapping("/{id}/cancel")
    public ValuationRefreshResponse cancel(@PathVariable UUID id) {
        return refresh.cancel(id);
    }

    /** Creates new pending work after a failure; repeated keys return the same new request. */
    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry a failed source change", description = "Creates a new event identity; does not reset attempts or alter the old request. MODEL_PUBLICATION retries belong to Python.")
    public ValuationRefreshResponse retry(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key) {
        return refresh.retry(id, key);
    }
}
