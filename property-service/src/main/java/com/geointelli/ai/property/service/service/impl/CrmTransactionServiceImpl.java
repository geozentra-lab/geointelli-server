package com.geointelli.ai.property.service.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.CrmActivityDTO;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.CrmPipelineDTO;
import com.geointelli.ai.property.service.dto.CrmTransactionCreateRequest;
import com.geointelli.ai.property.service.dto.CrmTransactionDTO;
import com.geointelli.ai.property.service.dto.CrmTransactionUpdateRequest;
import com.geointelli.ai.property.service.entity.CrmActivityType;
import com.geointelli.ai.property.service.entity.CrmSubjectType;
import com.geointelli.ai.property.service.entity.CrmTransaction;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;
import com.geointelli.ai.property.service.mapper.CrmWorkMapper;
import com.geointelli.ai.property.service.repository.CrmActivityRepository;
import com.geointelli.ai.property.service.repository.CrmTransactionRepository;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmTransactionService;
import com.geointelli.ai.property.service.service.CrmValidationService;
import com.geointelli.ai.property.service.service.CrmWorkReferenceService;
import com.geointelli.ai.property.service.service.CurrentUserService;

/** Tracks real, user-entered deal records without changing property facts or executing payments. */
@Service
@Transactional
public class CrmTransactionServiceImpl implements CrmTransactionService {
    private final CrmTransactionRepository transactions;
    private final CurrentUserService currentUser;
    private final CrmValidationService validation;
    private final CrmWorkReferenceService references;
    private final CrmActivityService activity;
    private final CrmActivityRepository history;
    private final CrmWorkMapper mapper;
    private final Clock clock;

    public CrmTransactionServiceImpl(CrmTransactionRepository transactions, CurrentUserService currentUser,
            CrmValidationService validation, CrmWorkReferenceService references, CrmActivityService activity,
            CrmActivityRepository history, CrmWorkMapper mapper, @Qualifier("crmWorkClock") Clock clock) {
        this.transactions = transactions;
        this.currentUser = currentUser;
        this.validation = validation;
        this.references = references;
        this.activity = activity;
        this.history = history;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public CrmTransactionDTO create(CrmTransactionCreateRequest request) {
        Long owner = currentUser.getCurrentUserEntity().getId();
        if (request == null) throw new IllegalArgumentException("Request body is required");
        var value = normalized(request);
        validation.validate(value);
        var links = references.resolve(owner, value.clientId(), value.leadId(), value.propertyId(), null);
        CrmTransaction transaction = new CrmTransaction();
        transaction.setUserId(owner);
        transaction.setClientId(links.clientId());
        transaction.setLeadId(links.leadId());
        transaction.setPropertyId(links.propertyId());
        transaction.setTitle(value.title());
        transaction.setDescription(value.description());
        transaction.setExpectedCloseDate(value.expectedCloseDate());
        transactions.saveAndFlush(transaction);
        record(transaction, CrmActivityType.TRANSACTION_CREATED, "Transaction created in stage PROSPECT");
        return mapper.toDTO(transaction);
    }

    @Override
    public CrmPageDTO<CrmTransactionDTO> list(int page, int size, CrmTransactionStage stage) {
        Long owner = currentUser.getCurrentUserEntity().getId();
        var pageable = validation.page(page, size);
        var result = stage == null ? transactions.findByUserId(owner, pageable)
            : transactions.findByUserIdAndStage(owner, stage, pageable);
        return new CrmPageDTO<>(result.map(mapper::toDTO).getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public CrmTransactionDTO get(Long id) { return mapper.toDTO(owned(id, false)); }

    @Override
    public CrmTransactionDTO update(Long id, CrmTransactionUpdateRequest request) {
        CrmTransaction transaction = owned(id, true);
        if (request == null) throw new IllegalArgumentException("Request body is required");
        var value = normalized(new CrmTransactionCreateRequest(
            request.isTitleProvided() ? request.getTitle() : transaction.getTitle(),
            request.isDescriptionProvided() ? request.getDescription() : transaction.getDescription(),
            transaction.getClientId(), transaction.getLeadId(), transaction.getPropertyId(),
            request.isExpectedCloseDateProvided() ? request.getExpectedCloseDate() : transaction.getExpectedCloseDate()));
        validation.validate(value);
        CrmTransactionStage next = request.isStageProvided() ? request.getStage() : transaction.getStage();
        if (next == null) throw new IllegalArgumentException("stage cannot be null");
        if (!transaction.getStage().canChangeTo(next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Unsupported transaction transition: " + transaction.getStage() + " -> " + next);
        }
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(transaction.getTitle(), value.title())) changed.add("title");
        if (!Objects.equals(transaction.getDescription(), value.description())) changed.add("description");
        if (!Objects.equals(transaction.getExpectedCloseDate(), value.expectedCloseDate())) changed.add("expectedCloseDate");
        boolean stageChanged = transaction.getStage() != next;
        String transition = "Stage " + transaction.getStage() + " -> " + next;
        if (stageChanged) changed.add("stage");
        if (!changed.isEmpty()) {
            transaction.setTitle(value.title());
            transaction.setDescription(value.description());
            transaction.setExpectedCloseDate(value.expectedCloseDate());
            if (stageChanged) transaction.setClosedAt(next == CrmTransactionStage.CLOSED ? now() : null);
            transaction.setStage(next);
            transactions.saveAndFlush(transaction);
            record(transaction, stageChanged ? CrmActivityType.TRANSACTION_STAGE_CHANGED : CrmActivityType.TRANSACTION_UPDATED,
                (stageChanged ? transition + "; " : "") + "Changed: " + String.join(", ", changed));
        }
        return mapper.toDTO(transaction);
    }

    @Override
    public CrmPipelineDTO pipeline() {
        Long owner = currentUser.getCurrentUserEntity().getId();
        Instant observedAt = now();
        var counts = new EnumMap<CrmTransactionStage, Long>(CrmTransactionStage.class);
        for (var stage : CrmTransactionStage.values()) counts.put(stage, 0L);
        long total = 0;
        for (var row : transactions.pipeline(owner)) {
            counts.put(row.getStage(), row.getCount());
            total += row.getCount();
        }
        return new CrmPipelineDTO(total, Collections.unmodifiableMap(counts), observedAt);
    }

    @Override
    public CrmPageDTO<CrmActivityDTO> history(Long id, int page, int size) {
        CrmTransaction transaction = owned(id, false);
        var result = history.findByUserIdAndSubjectTypeAndSubjectId(transaction.getUserId(), CrmSubjectType.TRANSACTION, id,
            validation.page(page, size));
        return new CrmPageDTO<>(result.map(mapper::toDTO).getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    private CrmTransaction owned(Long id, boolean lock) {
        validation.requirePositive(id, "transactionId");
        Long owner = currentUser.getCurrentUserEntity().getId();
        return (lock ? transactions.findOwnedForUpdate(id, owner) : transactions.findByIdAndUserId(id, owner))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    private CrmTransactionCreateRequest normalized(CrmTransactionCreateRequest value) {
        LocalDate date = value.expectedCloseDate();
        if (date != null && (date.getYear() < 1900 || date.getYear() > 2199)) {
            throw new IllegalArgumentException("expectedCloseDate must be within years 1900 to 2199");
        }
        return new CrmTransactionCreateRequest(validation.normalize(value.title()), validation.normalize(value.description()),
            value.clientId(), value.leadId(), value.propertyId(), date);
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    private void record(CrmTransaction value, CrmActivityType type, String description) {
        activity.record(value.getUserId(), value.getClientId(), CrmSubjectType.TRANSACTION, value.getId(), type, description);
    }
}
