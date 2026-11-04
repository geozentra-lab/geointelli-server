package com.geointelli.ai.property.service.controller;

import java.net.URI;
import org.springframework.http.ResponseEntity;
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
import com.geointelli.ai.property.service.dto.CrmPipelineDTO;
import com.geointelli.ai.property.service.dto.CrmTransactionCreateRequest;
import com.geointelli.ai.property.service.dto.CrmTransactionDTO;
import com.geointelli.ai.property.service.dto.CrmTransactionUpdateRequest;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;
import com.geointelli.ai.property.service.service.CrmTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Manual property-deal pipeline; not financial transaction processing. */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "CRM transactions", description = "Current-user scope, page >=0 and size 1-100. "
    + "Missing identity 401; invalid input 400; missing/foreign records 404; unsupported transitions 409.")
public class CrmTransactionController {
    private final CrmTransactionService transactions;

    @PostMapping
    @Operation(summary = "Create a property deal", description = "Requires title (1-160), owned clientId and existing "
        + "propertyId. Optional leadId must belong to that client and match its property when set. Description max 2000. "
        + "expectedCloseDate is optional YYYY-MM-DD, years 1900-2199; past dates are allowed. Starts PROSPECT (201). "
        + "Each POST creates a distinct deal. Associations cannot be edited later. Owner is taken from the bearer token.")
    public ResponseEntity<CrmTransactionDTO> create(@RequestBody CrmTransactionCreateRequest request) {
        var result = transactions.create(request);
        return ResponseEntity.created(URI.create("/api/transactions/" + result.id())).body(result);
    }

    @GetMapping
    @Operation(summary = "List my deals", description = "Newest ID first, optional stage filter: PROSPECT, VIEWING, "
        + "OFFER, UNDER_CONTRACT, CLOSED, CANCELLED. No matches return an empty page.")
    public CrmPageDTO<CrmTransactionDTO> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) CrmTransactionStage stage) {
        return transactions.list(page, size, stage);
    }

    @GetMapping("/pipeline")
    @Operation(summary = "Count my deals by stage", description = "All persisted records, independent of list pagination. "
        + "All six stages are present, with zero for stages without records. No cached or estimated counts.")
    public CrmPipelineDTO pipeline() { return transactions.pipeline(); }

    @GetMapping("/{id}")
    @Operation(summary = "Read my deal")
    public CrmTransactionDTO get(@PathVariable Long id) { return transactions.get(id); }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a deal and advance its stage", description = "Only title, description, expectedCloseDate "
        + "and stage can change. Omitted fields stay unchanged; description/expectedCloseDate accept null. "
        + "Transitions: PROSPECT -> VIEWING -> OFFER -> UNDER_CONTRACT -> CLOSED. Any open stage can become CANCELLED. "
        + "CLOSED/CANCELLED cannot reopen. Closing records a UTC closedAt once; no-op updates preserve it and add no activity. "
        + "Closing does not transfer property, process payment, convert a lead or complete linked tasks.")
    public CrmTransactionDTO update(@PathVariable Long id, @RequestBody CrmTransactionUpdateRequest request) {
        return transactions.update(id, request);
    }

    @GetMapping("/{id}/activities")
    @Operation(summary = "Read this deal's history")
    public CrmPageDTO<CrmActivityDTO> history(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return transactions.history(id, page, size); }
}
