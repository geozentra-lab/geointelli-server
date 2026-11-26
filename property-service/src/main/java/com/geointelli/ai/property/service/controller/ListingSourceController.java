package com.geointelli.ai.property.service.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.ListingSourceDTO;
import com.geointelli.ai.property.service.dto.ListingSourceFieldDTO;
import com.geointelli.ai.property.service.service.ListingSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Listing providers and their field mapping (SCRUM-118), read-only. Everything is under /api/admin, which the
 * security configuration limits to the Keycloak realm role admin: no token 401, other roles 403. The rows are the
 * machine-readable copy of docs/listing-source-field-mapping.md and are seeded by migration V45.
 */
@RestController
@RequestMapping("/api/admin/listing-sources")
@RequiredArgsConstructor
@Tag(name = "Listing sources", description = "Authorized listing providers, their coverage, refresh method and "
        + "the mapping of every provider field to the listings table. Asking-price fields and completed-sale "
        + "fields are separate groups. Admin only: no token 401, not admin 403, unknown code 404, unknown filter 400.")
public class ListingSourceController {
    private final ListingSourceService service;

    /**
     * Lists every listing provider, newest first, with its field count.
     */
    @GetMapping
    @Operation(summary = "List listing providers",
            description = "Every provider with its authorization status, coverage, refresh method and field "
                    + "count. The field mapping itself is on GET /{code}.")
    @ApiResponse(responseCode = "200", description = "Providers, newest first; empty list when none is defined")
    @ApiResponse(responseCode = "401", description = "No token")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    public ResponseEntity<List<ListingSourceDTO>> list() {
        return ResponseEntity.ok(service.list());
    }

    /**
     * Returns one provider with its full field mapping.
     *
     * @param code provider code such as REALTOR_COM, any letter case
     */
    @GetMapping("/{code}")
    @Operation(summary = "Get a listing provider with its field mapping",
            description = "The provider row plus every mapping line in document order: provider field, target "
                    + "column of the listings table (null when not imported), type, availability and note.")
    @ApiResponse(responseCode = "200", description = "Provider with fields")
    @ApiResponse(responseCode = "401", description = "No token")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    @ApiResponse(responseCode = "404", description = "No provider has that code")
    public ResponseEntity<ListingSourceDTO> get(
            @Parameter(description = "Provider code, any letter case", example = "REALTOR_COM")
            @PathVariable String code) {
        return ResponseEntity.ok(service.get(code));
    }

    /**
     * Returns the field mapping of one provider, optionally filtered by group and availability.
     *
     * @param code         provider code, any letter case
     * @param group        IDENTIFIER, TIMESTAMP, STATUS, ASKING_PRICE, SALE_PRICE, LOCATION, DESCRIPTION or NOT_MAPPED
     * @param availability AVAILABLE, PARTIAL, UNAVAILABLE or EXCLUDED
     */
    @GetMapping("/{code}/fields")
    @Operation(summary = "Get the field mapping of a listing provider",
            description = "Mapping lines in document order. Filter by group (IDENTIFIER, TIMESTAMP, STATUS, "
                    + "ASKING_PRICE, SALE_PRICE, LOCATION, DESCRIPTION, NOT_MAPPED) and/or availability "
                    + "(AVAILABLE, PARTIAL, UNAVAILABLE, EXCLUDED); both optional and case-insensitive. "
                    + "An empty list is a valid answer.")
    @ApiResponse(responseCode = "200", description = "Mapping lines, possibly empty")
    @ApiResponse(responseCode = "400", description = "Unknown group or availability; the message lists the allowed values")
    @ApiResponse(responseCode = "401", description = "No token")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    @ApiResponse(responseCode = "404", description = "No provider has that code")
    public ResponseEntity<List<ListingSourceFieldDTO>> fields(
            @Parameter(description = "Provider code, any letter case", example = "REALTOR_COM")
            @PathVariable String code,
            @Parameter(description = "Field group", example = "ASKING_PRICE")
            @RequestParam(required = false) String group,
            @Parameter(description = "Availability", example = "EXCLUDED")
            @RequestParam(required = false) String availability) {
        return ResponseEntity.ok(service.fields(code, group, availability));
    }
}
