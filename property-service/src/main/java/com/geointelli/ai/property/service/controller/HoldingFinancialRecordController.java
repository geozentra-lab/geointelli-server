package com.geointelli.ai.property.service.controller;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.FinancialRecordDTO;
import com.geointelli.ai.property.service.dto.FinancialRecordQuery;
import com.geointelli.ai.property.service.dto.FinancialRecordRequest;
import com.geointelli.ai.property.service.dto.PortfolioCreate;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;
import com.geointelli.ai.property.service.service.HoldingFinancialRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Records actual income and expenses for a holding belonging to the bearer-token user. */
@RestController
@RequestMapping("/api/portfolios/{id}/holdings/{holdingId}/financial-records")
@Tag(name = "Holding financial records", description = "User-reported actual transactions, separate from scenarios and projected rent.")
@SecurityRequirement(name = "bearerAuth")
public class HoldingFinancialRecordController {
    private final HoldingFinancialRecordService records;

    public HoldingFinancialRecordController(HoldingFinancialRecordService records) {
        this.records = records;
    }

    @PostMapping
    @Operation(summary = "Record actual income or expense", description = "Requires category, positive amount and transactionDate. "
        + "Amounts are USD totals for the full property, before ownership-share allocation. Category determines INCOME/EXPENSE. "
        + "Optional covered period uses inclusive dates and does not prorate or schedule transactions. "
        + "Creates only an actual record; does not change a holding's monthly rent, loan or any projected scenario. "
        + "Returns 201; missing/foreign parent returns 404; invalid input 400; missing identity 401.")
    public ResponseEntity<FinancialRecordDTO> create(@PathVariable Long id, @PathVariable Long holdingId,
            @Validated(PortfolioCreate.class) @RequestBody FinancialRecordRequest request) {
        FinancialRecordDTO result = records.create(id, holdingId, request);
        return ResponseEntity.created(URI.create("/api/portfolios/" + id + "/holdings/" + holdingId
            + "/financial-records/" + result.id())).body(result);
    }

    @GetMapping
    @Operation(summary = "List actual financial records", description = "Zero-based page; size 1 to 100, default 20. "
        + "Sorted by transactionDate descending, then ID descending. fromDate/toDate are inclusive transaction-date filters, "
        + "not covered-period overlap filters. Optional category must match an enum value. Empty data stays empty.")
    public PortfolioPageDTO<FinancialRecordDTO> list(@PathVariable Long id, @PathVariable Long holdingId,
            @Valid @ModelAttribute FinancialRecordQuery query) {
        return records.list(id, holdingId, query);
    }

    @GetMapping("/{recordId}")
    @Operation(summary = "Get actual financial record details", description = "Returns one record after checking "
        + "portfolio ownership, holding membership and record membership. Missing or inaccessible records return 404; "
        + "invalid IDs return 400 and missing bearer identity returns 401.")
    public FinancialRecordDTO get(@PathVariable Long id, @PathVariable Long holdingId, @PathVariable Long recordId) {
        return records.get(id, holdingId, recordId);
    }

    @PatchMapping("/{recordId}")
    @Operation(summary = "Correct an actual financial record", description = "Omitted fields remain unchanged. "
        + "Explicit null clears description or period dates; category, amount, currency and transactionDate cannot be cleared. "
        + "Period dates must both exist or both be null after the correction. Corrections overwrite supplied facts, "
        + "retain createdAt and update updatedAt; a revision history is not retained. Record cannot be moved to another holding.")
    public FinancialRecordDTO update(@PathVariable Long id, @PathVariable Long holdingId,
            @PathVariable Long recordId, @Valid @RequestBody FinancialRecordRequest request) {
        return records.update(id, holdingId, recordId, request);
    }

    @DeleteMapping("/{recordId}")
    @Operation(summary = "Delete an actual financial record", description = "Permanent removal of this record only; "
        + "returns 204. The portfolio, holding, property and images remain. Missing/foreign record returns 404. "
        + "Deleting the parent holding or portfolio also permanently deletes its financial records.")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable Long holdingId, @PathVariable Long recordId) {
        records.delete(id, holdingId, recordId);
        return ResponseEntity.noContent().build();
    }
}
