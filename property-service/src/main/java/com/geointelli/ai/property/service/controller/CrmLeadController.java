package com.geointelli.ai.property.service.controller;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.CrmLeadCreateRequest;
import com.geointelli.ai.property.service.dto.CrmLeadDTO;
import com.geointelli.ai.property.service.dto.CrmLeadUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.entity.LeadStage;
import com.geointelli.ai.property.service.service.CrmLeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Leads reuse owned clients as their contacts; there is no public, anonymous submission endpoint. */
@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "CRM leads", description = "Current-user scope. Missing/foreign records 404; missing identity 401; "
    + "invalid input 400; invalid transitions or idempotency conflicts 409.")
public class CrmLeadController {
    private final CrmLeadService leads;

    @PostMapping
    @Operation(summary = "Capture a lead for my client", description = "Requires an owned clientId, title (1-160), "
        + "source label (1-80, e.g. WEBSITE) and Idempotency-Key header (1-100 letters/digits/._:-; UUID recommended). "
        + "Optional propertyId must identify an existing property; notes max 2000. Stage starts NEW. "
        + "New submission returns 201; same owner/key/normalized body returns 200 with the current lead and no extra "
        + "activity. Reusing a key with different input returns 409. Keys are retained permanently with the lead; "
        + "a new key means an intentionally new enquiry, even for the same contact/property. Replays never reset later edits.")
    public ResponseEntity<CrmLeadDTO> create(@RequestBody CrmLeadCreateRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        var result = leads.create(request, idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
            .location(URI.create("/api/leads/" + result.lead().id())).body(result.lead());
    }

    @GetMapping
    @Operation(summary = "List my leads", description = "Zero-based page, size 1-100; newest ID first. "
        + "Optional stage filter: NEW, CONTACTED, QUALIFIED, CONVERTED, LOST. No matches return an empty page.")
    public CrmPageDTO<CrmLeadDTO> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) LeadStage stage) {
        return leads.list(page, size, stage);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read my lead")
    public CrmLeadDTO get(@PathVariable Long id) { return leads.get(id); }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a lead or advance its stage", description = "Allowed: NEW -> CONTACTED -> QUALIFIED "
        + "-> CONVERTED; any open stage may become LOST. CONVERTED/LOST are terminal, but other fields remain editable. "
        + "Same stage is a no-op. CONVERTED records a won enquiry only; it does not create a transaction or property. "
        + "Omitted fields stay unchanged; notes and propertyId accept null. Title/source/stage cannot be cleared; "
        + "clientId and submission key cannot be changed. No-op patches do not add activity.")
    public CrmLeadDTO update(@PathVariable Long id, @RequestBody CrmLeadUpdateRequest request) {
        return leads.update(id, request);
    }
}
