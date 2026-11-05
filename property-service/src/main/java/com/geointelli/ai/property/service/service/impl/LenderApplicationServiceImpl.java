package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.LenderApplicationCreateRequest;
import com.geointelli.ai.property.service.dto.LenderApplicationDTO;
import com.geointelli.ai.property.service.dto.LenderApplicationImportRequest;
import com.geointelli.ai.property.service.dto.LenderApplicationImportResult;
import com.geointelli.ai.property.service.dto.LenderApplicationUpdateRequest;
import com.geointelli.ai.property.service.dto.LenderPipelineDTO;
import com.geointelli.ai.property.service.entity.LenderApplication;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;
import com.geointelli.ai.property.service.mapper.LenderApplicationMapper;
import com.geointelli.ai.property.service.repository.LenderApplicationRepository;
import com.geointelli.ai.property.service.service.LenderApplicationService;
import com.geointelli.ai.property.service.service.LenderApplicationValidationService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import lombok.RequiredArgsConstructor;

/** Transactional application tracking. Organization locks serialize writes and source-identity upserts. */
@Service
@Transactional
@RequiredArgsConstructor
public class LenderApplicationServiceImpl implements LenderApplicationService {
    private final LenderApplicationRepository applications;
    private final LenderOrganizationService organizations;
    private final LenderApplicationValidationService validation;
    private final LenderApplicationMapper mapper;

    @Override
    public LenderApplicationDTO create(Long actor, Long organizationId, LenderApplicationCreateRequest request) {
        organizations.requireAccess(actor, organizationId, true);
        if (request == null) throw new IllegalArgumentException("Request body is required");
        LenderApplication value = new LenderApplication();
        value.setOrganizationId(organizationId);
        value.setCreatedByUserId(actor);
        value.setApplicantName(request.applicantName());
        value.setApplicantEmail(request.applicantEmail());
        value.setPropertyId(request.propertyId());
        value.setRequestedAmount(request.requestedAmount());
        value.setCurrency(request.currency() == null ? "USD" : request.currency());
        value.setNotes(request.notes());
        validation.validate(value);
        return mapper.toDTO(applications.saveAndFlush(value));
    }

    @Override
    public CrmPageDTO<LenderApplicationDTO> list(Long actor, Long organizationId, int page, int size, LenderApplicationStage stage) {
        organizations.requireAccess(actor, organizationId, false);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be >= 0 and size between 1 and 100");
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        var result = stage == null ? applications.findByOrganizationId(organizationId, pageable)
            : applications.findByOrganizationIdAndStage(organizationId, stage, pageable);
        return new CrmPageDTO<>(result.map(mapper::toDTO).getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public LenderApplicationDTO get(Long actor, Long organizationId, Long id) {
        organizations.requireAccess(actor, organizationId, false);
        return mapper.toDTO(owned(organizationId, id));
    }

    @Override
    public LenderApplicationDTO update(Long actor, Long organizationId, Long id, LenderApplicationUpdateRequest request) {
        organizations.requireAccess(actor, organizationId, true);
        LenderApplication value = owned(organizationId, id);
        if (request == null) throw new IllegalArgumentException("Request body is required");
        LenderApplicationDTO before = mapper.toDTO(value);
        LenderApplicationStage next = request.isStageProvided() ? request.getStage() : value.getStage();
        transition(value.getStage(), next);
        if (request.isApplicantNameProvided()) value.setApplicantName(request.getApplicantName());
        if (request.isApplicantEmailProvided()) value.setApplicantEmail(request.getApplicantEmail());
        if (request.isPropertyIdProvided()) value.setPropertyId(request.getPropertyId());
        if (request.isRequestedAmountProvided()) value.setRequestedAmount(request.getRequestedAmount());
        if (request.isCurrencyProvided()) value.setCurrency(request.getCurrency());
        if (request.isApprovedAmountProvided()) value.setApprovedAmount(request.getApprovedAmount());
        if (request.isFundedAmountProvided()) value.setFundedAmount(request.getFundedAmount());
        if (request.isNotesProvided()) value.setNotes(request.getNotes());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (next == LenderApplicationStage.APPROVED && value.getStage() != next) value.setApprovedAt(now);
        if (next == LenderApplicationStage.FUNDED && value.getStage() != next) value.setFundedAt(now);
        value.setStage(next);
        validation.validate(value);
        if (!before.equals(mapper.toDTO(value))) applications.saveAndFlush(value);
        return mapper.toDTO(value);
    }

    @Override
    public LenderPipelineDTO pipeline(Long actor, Long organizationId) {
        organizations.requireAccess(actor, organizationId, false);
        var stages = new EnumMap<LenderApplicationStage, LenderPipelineDTO.StageTotals>(LenderApplicationStage.class);
        for (LenderApplicationStage stage : LenderApplicationStage.values())
            stages.put(stage, new LenderPipelineDTO.StageTotals(0, BigDecimal.ZERO.setScale(2)));
        long total = 0, approvedCount = 0, fundedCount = 0;
        BigDecimal approved = BigDecimal.ZERO.setScale(2), funded = BigDecimal.ZERO.setScale(2);
        for (var row : applications.pipeline(organizationId)) {
            stages.put(row.getStage(), new LenderPipelineDTO.StageTotals(row.getCount(), zero(row.getRequested())));
            total += row.getCount();
            if (row.getStage() == LenderApplicationStage.APPROVED || row.getStage() == LenderApplicationStage.FUNDED) {
                approvedCount += row.getCount();
                approved = approved.add(zero(row.getApproved()));
            }
            if (row.getStage() == LenderApplicationStage.FUNDED) {
                fundedCount += row.getCount();
                funded = funded.add(zero(row.getFunded()));
            }
        }
        return new LenderPipelineDTO(organizationId, "USD", total, Collections.unmodifiableMap(stages),
            approvedCount, approved, fundedCount, funded, Instant.now().truncatedTo(ChronoUnit.MICROS),
            "All recorded applications; current status, not a historical period. Approved includes APPROVED and FUNDED; "
                + "funded includes FUNDED only. Withdrawn approvals are excluded. Recorded USD amounts; no valuation or credit decision.");
    }

    @Override
    public LenderApplicationImportResult.RowResult importRow(Long actor, Long organizationId, String source,
            int rowNumber, LenderApplicationImportRequest.Row row) {
        organizations.requireAccess(actor, organizationId, true);
        source = validation.source(source);
        if (row == null) throw new IllegalArgumentException("Import row is required");
        String externalId = validation.externalId(row.externalId());
        LenderApplication value = applications.findByOrganizationIdAndSourceAndExternalId(organizationId, source, externalId)
            .orElse(null);
        boolean created = value == null;
        LenderApplicationDTO before = created ? null : mapper.toDTO(value);
        if (created) {
            value = new LenderApplication();
            value.setOrganizationId(organizationId);
            value.setCreatedByUserId(actor);
            value.setSource(source);
            value.setExternalId(externalId);
        } else {
            transition(value.getStage(), row.stage());
            // Once observed, approval and funding timestamps identify the same recorded milestones.
            if ((value.getApprovedAt() != null && !value.getApprovedAt().equals(micros(row.approvedAt())))
                    || (value.getFundedAt() != null && !value.getFundedAt().equals(micros(row.fundedAt()))))
                throw new IllegalArgumentException("Recorded approval/funding timestamps cannot change");
        }
        value.setApplicantName(row.applicantName());
        value.setApplicantEmail(row.applicantEmail());
        value.setPropertyId(row.propertyId());
        value.setRequestedAmount(row.requestedAmount());
        value.setCurrency(row.currency());
        value.setStage(row.stage());
        value.setApprovedAmount(row.approvedAmount());
        value.setFundedAmount(row.fundedAmount());
        value.setApprovedAt(row.approvedAt());
        value.setFundedAt(row.fundedAt());
        value.setNotes(row.notes());
        validation.validate(value);
        if (!created && before.equals(mapper.toDTO(value)))
            return new LenderApplicationImportResult.RowResult(rowNumber, "UNCHANGED", value.getId(), null);
        applications.saveAndFlush(value);
        return new LenderApplicationImportResult.RowResult(rowNumber, created ? "CREATED" : "UPDATED", value.getId(), null);
    }

    private LenderApplication owned(Long organizationId, Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("id must be positive");
        return applications.findByIdAndOrganizationId(id, organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private void transition(LenderApplicationStage previous, LenderApplicationStage next) {
        if (next == null) throw new IllegalArgumentException("stage cannot be null");
        if (!previous.canChangeTo(next)) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Unsupported application transition: " + previous + " -> " + next);
    }

    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2); }
    private Instant micros(Instant value) { return value == null ? null : value.truncatedTo(ChronoUnit.MICROS); }
}

