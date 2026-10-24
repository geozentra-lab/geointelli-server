package com.geointelli.ai.property.service.controller;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.PortfolioCreate;
import com.geointelli.ai.property.service.dto.PortfolioHoldingDTO;
import com.geointelli.ai.property.service.dto.PortfolioHoldingRequest;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;
import com.geointelli.ai.property.service.service.PortfolioHoldingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Exposes holdings only beneath a portfolio owned by the bearer-token user. */
@RestController
@RequestMapping("/api/portfolios/{id}/holdings")
@Tag(name = "Portfolio holdings")
@SecurityRequirement(name = "bearerAuth")
public class PortfolioHoldingController {
    private final PortfolioHoldingService holdings;

    public PortfolioHoldingController(PortfolioHoldingService holdings) {
        this.holdings = holdings;
    }

    @PostMapping
    @Operation(summary = "Add a property to my portfolio", description = "Requires an internal propertyId. "
        + "Duplicate property in the same portfolio returns 409. The same property may belong to different portfolios. "
        + "Ownership share defaults to 100%. All amounts are reported USD totals for the full property; "
        + "no valuation or performance calculation is performed.")
    public ResponseEntity<PortfolioHoldingDTO> create(@PathVariable Long id,
            @Validated(PortfolioCreate.class) @RequestBody PortfolioHoldingRequest request) {
        PortfolioHoldingDTO result = holdings.create(id, request);
        return ResponseEntity.created(URI.create("/api/portfolios/" + id + "/holdings/" + result.id())).body(result);
    }

    @GetMapping
    @Operation(summary = "List my portfolio holdings", description = "Zero-based pagination, size 1–100; newest ID first.")
    public PortfolioPageDTO<PortfolioHoldingDTO> list(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return holdings.list(id, page, size);
    }

    @GetMapping("/{holdingId}")
    @Operation(summary = "Get a holding", description = "Checks portfolio ownership and holding membership; otherwise 404.")
    public PortfolioHoldingDTO get(@PathVariable Long id, @PathVariable Long holdingId) {
        return holdings.get(id, holdingId);
    }

    @PatchMapping("/{holdingId}")
    @Operation(summary = "Update acquisition, financing or occupancy", description = "Omitted fields remain unchanged. "
        + "Optional fields can be cleared with null. Financing/occupancy objects replace their entire section; "
        + "null resets that section to UNKNOWN. propertyId is immutable. Currency is USD.")
    public PortfolioHoldingDTO update(@PathVariable Long id, @PathVariable Long holdingId,
            @Valid @RequestBody PortfolioHoldingRequest request) {
        return holdings.update(id, holdingId, request);
    }

    @DeleteMapping("/{holdingId}")
    @Operation(summary = "Remove a holding", description = "Permanently deletes the holding and its financial records, preserving "
        + "the underlying property and images. Returns 204; missing or inaccessible holding is 404.")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable Long holdingId) {
        holdings.delete(id, holdingId);
        return ResponseEntity.noContent().build();
    }
}
