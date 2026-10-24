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
import com.geointelli.ai.property.service.dto.PortfolioDTO;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;
import com.geointelli.ai.property.service.dto.PortfolioRequest;
import com.geointelli.ai.property.service.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Exposes portfolio management for the bearer-token owner, independently of persona. */
@RestController
@RequestMapping("/api/portfolios")
@Tag(name = "Portfolios", description = "Bearer token required to identify the owner. All amounts use USD.")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityRequirement(name = "bearerAuth")
public class PortfolioController {
    private final PortfolioService portfolios;

    public PortfolioController(PortfolioService portfolios) {
        this.portfolios = portfolios;
    }

    @PostMapping
    @Operation(summary = "Create a portfolio", description = "Owner comes from the bearer token; no user ID is accepted.")
    public ResponseEntity<PortfolioDTO> create(@Validated(PortfolioCreate.class) @RequestBody PortfolioRequest request) {
        PortfolioDTO result = portfolios.create(request);
        return ResponseEntity.created(URI.create("/api/portfolios/" + result.id())).body(result);
    }

    @GetMapping
    @Operation(summary = "List my portfolios", description = "Zero-based pagination, size 1–100; newest ID first.")
    public PortfolioPageDTO<PortfolioDTO> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return portfolios.list(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get my portfolio", description = "Returns 404 for missing or other users' portfolios. "
        + "Holdings are available from the separately paginated holdings endpoint.")
    public PortfolioDTO get(@PathVariable Long id) {
        return portfolios.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a portfolio", description = "Omitted fields remain unchanged; description:null clears it. "
        + "Name cannot be null or blank.")
    public PortfolioDTO update(@PathVariable Long id, @Valid @RequestBody PortfolioRequest request) {
        return portfolios.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a portfolio and all its holdings", description = "Permanent deletion, including holding financial records. "
        + "The underlying properties, parcels, sales and images are preserved. Returns 204; missing/foreign portfolio is 404.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        portfolios.delete(id);
        return ResponseEntity.noContent().build();
    }
}
