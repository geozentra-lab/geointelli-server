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
import com.geointelli.ai.property.service.dto.CrmClientCreateRequest;
import com.geointelli.ai.property.service.dto.CrmClientDTO;
import com.geointelli.ai.property.service.dto.CrmClientUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.service.CrmClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Manages the bearer-token user's clients without persona-based permission grants. */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "CRM clients", description = "Current-user scope only; organization sharing is not enabled. "
    + "Missing/foreign IDs return 404; missing identity 401; invalid input 400; duplicate or conflicting changes 409.")
public class CrmClientController {
    private final CrmClientService clients;

    /** Creates a normalized contact and its history entry atomically. */
    @PostMapping
    @Operation(summary = "Create my client", description = "Name (1-120 characters) and at least one email or phone required. "
        + "Text is trimmed; email is lowercased and unique per owner (409 on duplicates). Phone supports 7-15 digits, "
        + "an optional leading + and common separators, with a maximum of 40 characters. Phone-only contacts can share a "
        + "number; names/phone numbers are not used to merge contacts automatically. Notes are optional, max 2000. Returns 201.")
    public ResponseEntity<CrmClientDTO> create(@RequestBody CrmClientCreateRequest request) {
        CrmClientDTO result = clients.create(request);
        return ResponseEntity.created(URI.create("/api/clients/" + result.id())).body(result);
    }

    @GetMapping
    @Operation(summary = "List my clients", description = "Zero-based page, size 1-100; ID descending. No data returns an empty page.")
    public CrmPageDTO<CrmClientDTO> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return clients.list(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read my client")
    public CrmClientDTO get(@PathVariable Long id) { return clients.get(id); }

    @PatchMapping("/{id}")
    @Operation(summary = "Update my client", description = "Only supplied fields change. email, phone and notes can be "
        + "cleared with null, provided at least one contact method remains. Name cannot be cleared. An empty or identical "
        + "patch returns the current record without adding activity. Owner cannot be changed.")
    public CrmClientDTO update(@PathVariable Long id, @RequestBody CrmClientUpdateRequest request) {
        return clients.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an unreferenced client", description = "Returns 204. A client referenced by any lead, task "
        + "or transaction cannot be deleted (409), including closed records. Historical activities, including the deletion, remain in "
        + "GET /api/crm/activities. No property records are changed.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        clients.delete(id);
        return ResponseEntity.noContent().build();
    }
}
