package com.geointelli.ai.property.service.controller;

import java.net.URI;

import com.geointelli.ai.property.service.dto.LenderCollateralAnalysisDTO;
import com.geointelli.ai.property.service.service.LenderCollateralAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.LenderApplicationCreateRequest;
import com.geointelli.ai.property.service.dto.LenderApplicationDTO;
import com.geointelli.ai.property.service.dto.LenderApplicationImportRequest;
import com.geointelli.ai.property.service.dto.LenderApplicationImportResult;
import com.geointelli.ai.property.service.dto.LenderApplicationUpdateRequest;
import com.geointelli.ai.property.service.dto.LenderPipelineDTO;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.LenderApplicationImportService;
import com.geointelli.ai.property.service.service.LenderApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Lender tracking endpoints. Resolves the authenticated user before the transactional service begins. */
@RestController
@RequestMapping("/api/lender")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Lender applications", description = "Manual/partner-imported tracking, not mortgage origination. "
    + "organizationId is required on every request and checked against stored membership. Missing token 401; "
    + "invalid input 400; foreign/missing organization or application 404; invalid stage transition 409. "
    + "USD only; amounts are positive with at most two decimals. Applicant email, propertyId and notes are optional.")
public class LenderApplicationController {
    private final LenderApplicationService applications;
    private final LenderApplicationImportService imports;
    private final LenderCollateralAnalysisService collateralAnalysis;
    private final CurrentUserService currentUser;

    @PostMapping("/applications")
    @Operation(summary = "Create a DRAFT application", description = "Requires applicantName (1-160) and requestedAmount "
        + "(up to 9999999999999999.99). Currency defaults to USD. Optional applicantEmail max 254, existing propertyId, "
        + "notes max 2000. Every manual POST creates a separate record. Actor and organization are not taken from the body.")
    public ResponseEntity<LenderApplicationDTO> create(@RequestParam Long organizationId,
            @RequestBody LenderApplicationCreateRequest request) {
        var result = applications.create(actor(), organizationId, request);
        return ResponseEntity.created(URI.create("/api/lender/applications/" + result.id()
            + "?organizationId=" + organizationId)).body(result);
    }

    @GetMapping("/applications")
    @Operation(summary = "List organization applications", description = "Newest ID first; page >=0, size 1-100. "
        + "Optional stage filter. Empty results return an empty page.")
    public CrmPageDTO<LenderApplicationDTO> list(@RequestParam Long organizationId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LenderApplicationStage stage) {
        return applications.list(actor(), organizationId, page, size, stage);
    }

    @GetMapping("/applications/{id}")
    @Operation(summary = "Read application details in my organization")
    public LenderApplicationDTO get(@RequestParam Long organizationId, @PathVariable Long id) {
        return applications.get(actor(), organizationId, id);
    }

    @GetMapping("/applications/{id}/collateral-analysis")
    @Operation(summary = "Collateral value and LTV of an application", description = "Read-only. Loan amount is "
        + "fundedAmount, else approvedAmount, else requestedAmount (loanAmountBasis). Collateral is the stored model "
        + "valuation of the linked property (SCRUM-58), unchanged, with its prediction date and confidence. "
        + "LTV = loanAmount / value x 100, two decimals: AVAILABLE for a supported valuation, LIMITED for a limited one "
        + "(with the model reasons), UNAVAILABLE with a null percent and a reason (NO_COLLATERAL_PROPERTY, "
        + "COLLATERAL_PROPERTY_NOT_FOUND, VALUATION_UNAVAILABLE, INVALID_VALUATION) otherwise. "
        + "Informational only; not a credit decision or approval.")
    public LenderCollateralAnalysisDTO collateralAnalysis(@RequestParam Long organizationId, @PathVariable Long id) {
        return collateralAnalysis.analyze(actor(), organizationId, id);
    }

    @PatchMapping("/applications/{id}")
    @Operation(summary = "Update application fields or stage", description = "Omitted fields stay unchanged; "
        + "applicantEmail, propertyId and notes may be null. Transitions: DRAFT -> SUBMITTED -> UNDER_REVIEW -> "
        + "APPROVED -> FUNDED; UNDER_REVIEW -> REJECTED; any nonterminal stage -> WITHDRAWN. "
        + "FUNDED, REJECTED and WITHDRAWN cannot change stage. Same-stage corrections are allowed. "
        + "APPROVED requires approvedAmount <= requestedAmount; entering APPROVED records approvedAt in UTC. "
        + "FUNDED requires fundedAmount <= approvedAmount; entering FUNDED records fundedAt in UTC. "
        + "Repeated no-op updates preserve milestone and audit timestamps. Approval/funding timestamps, source and "
        + "externalId cannot be set through PATCH. This only records status; it does not approve credit or transfer funds.")
    public LenderApplicationDTO update(@RequestParam Long organizationId, @PathVariable Long id,
            @RequestBody LenderApplicationUpdateRequest request) {
        return applications.update(actor(), organizationId, id, request);
    }

    @GetMapping("/pipeline")
    @Operation(summary = "Summarize the organization's recorded application pipeline", description = "All-time, "
        + "current-status counts and requested USD amounts for every stage, including zeros. approvedCount/approvedAmount "
        + "include APPROVED and FUNDED records; fundedCount/fundedAmount include FUNDED records only. "
        + "Withdrawn approvals are excluded. Totals are independent of pagination and not historical period flows.")
    public LenderPipelineDTO pipeline(@RequestParam Long organizationId) {
        return applications.pipeline(actor(), organizationId);
    }

    @PostMapping("/applications/import")
    @Operation(summary = "Import 1-100 complete partner snapshots", description = "JSON body: source (1-80) and rows. "
        + "Each row requires externalId (1-160, case-sensitive), applicantName, requestedAmount, currency USD and stage. "
        + "Source is trimmed and uppercased. Identity is organization + source + externalId. Optional fields are replaced "
        + "by the snapshot, including nulls. New snapshots may start at any consistent recorded stage; APPROVED/FUNDED "
        + "require approvedAmount/approvedAt and FUNDED also requires fundedAmount/fundedAt. Dates must be UTC instants "
        + "between 1900 and now, approvedAt <= fundedAt. Times normalize to PostgreSQL microseconds. Existing snapshots "
        + "must follow the same transitions as PATCH and cannot change recorded milestone dates. "
        + "Rows run in input order, commit independently and return CREATED, UPDATED, UNCHANGED or REJECTED. "
        + "An identical retry creates no extra application or audit update. Rejections include a one-based row number "
        + "and safe code: INVALID_ROW, INVALID_TRANSITION or CONFLICT_RETRY_ROW. An invalid organization rejects "
        + "the request; if access is revoked mid-import, already committed rows remain committed. There is no background job.")
    public LenderApplicationImportResult importApplications(@RequestParam Long organizationId,
            @RequestBody LenderApplicationImportRequest request) {
        return imports.importApplications(actor(), organizationId, request);
    }

    private Long actor() { return currentUser.getCurrentUserEntity().getId(); }
}

