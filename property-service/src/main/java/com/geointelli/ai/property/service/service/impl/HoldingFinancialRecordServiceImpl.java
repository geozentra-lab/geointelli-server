package com.geointelli.ai.property.service.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.FinancialRecordDTO;
import com.geointelli.ai.property.service.dto.FinancialRecordQuery;
import com.geointelli.ai.property.service.dto.FinancialRecordRequest;
import com.geointelli.ai.property.service.dto.PortfolioCreate;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;
import com.geointelli.ai.property.service.entity.HoldingFinancialRecord;
import com.geointelli.ai.property.service.entity.PortfolioHolding;
import com.geointelli.ai.property.service.mapper.FinancialRecordMapper;
import com.geointelli.ai.property.service.repository.HoldingFinancialRecordRepository;
import com.geointelli.ai.property.service.repository.PortfolioHoldingRepository;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.HoldingFinancialRecordService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

/** Checks the complete owner/portfolio/holding/record chain before reading or modifying financial records. */
@Service
@Transactional
public class HoldingFinancialRecordServiceImpl implements HoldingFinancialRecordService {
    private final HoldingFinancialRecordRepository records;
    private final PortfolioRepository portfolios;
    private final PortfolioHoldingRepository holdings;
    private final CurrentUserService currentUser;
    private final FinancialRecordMapper mapper;
    private final Validator validator;

    public HoldingFinancialRecordServiceImpl(HoldingFinancialRecordRepository records, PortfolioRepository portfolios,
            PortfolioHoldingRepository holdings, CurrentUserService currentUser,
            FinancialRecordMapper mapper, Validator validator) {
        this.records = records;
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    public FinancialRecordDTO create(Long portfolioId, Long holdingId, FinancialRecordRequest request) {
        PortfolioHolding holding = ownedHolding(portfolioId, holdingId, true);
        validate(request, PortfolioCreate.class);
        validatePeriod(null, request);
        HoldingFinancialRecord record = new HoldingFinancialRecord();
        record.setHolding(holding);
        apply(record, request);
        return mapper.toDTO(records.saveAndFlush(record));
    }

    @Override
    public PortfolioPageDTO<FinancialRecordDTO> list(Long portfolioId, Long holdingId, FinancialRecordQuery query) {
        ownedHolding(portfolioId, holdingId, false);
        validate(query, Default.class);
        Page<HoldingFinancialRecord> page = records.findRecords(holdingId, query.getFromDate(),
            query.getToDate(), query.getCategory(), PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.DESC, "transactionDate", "id")));
        List<FinancialRecordDTO> content = new ArrayList<>();
        for (HoldingFinancialRecord record : page) content.add(mapper.toDTO(record));
        return new PortfolioPageDTO<>(content, page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public FinancialRecordDTO get(Long portfolioId, Long holdingId, Long recordId) {
        ownedHolding(portfolioId, holdingId, false);
        return mapper.toDTO(findRecord(holdingId, recordId));
    }

    @Override
    public FinancialRecordDTO update(Long portfolioId, Long holdingId, Long recordId, FinancialRecordRequest request) {
        ownedHolding(portfolioId, holdingId, true);
        HoldingFinancialRecord record = findRecord(holdingId, recordId);
        validate(request, Default.class);
        validatePeriod(record, request);
        apply(record, request);
        return mapper.toDTO(records.saveAndFlush(record));
    }

    @Override
    public void delete(Long portfolioId, Long holdingId, Long recordId) {
        ownedHolding(portfolioId, holdingId, true);
        records.delete(findRecord(holdingId, recordId));
    }

    /** Uses the same parent-row lock as holding updates/deletes, preventing races with parent deletion. */
    private PortfolioHolding ownedHolding(Long portfolioId, Long holdingId, boolean lock) {
        requirePositive(portfolioId, "portfolioId");
        requirePositive(holdingId, "holdingId");
        Long userId = currentUser.getCurrentUserEntity().getId();
        (lock ? portfolios.findOwnedForUpdate(portfolioId, userId)
            : portfolios.findByIdAndUser_Id(portfolioId, userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
        return holdings.findByIdAndPortfolio_Id(holdingId, portfolioId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Holding not found in this portfolio"));
    }

    private HoldingFinancialRecord findRecord(Long holdingId, Long recordId) {
        requirePositive(recordId, "recordId");
        return records.findByIdAndHolding_Id(recordId, holdingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Financial record not found in this holding"));
    }

    private <T> void validate(T request, Class<?> group) {
        if (request == null) throw new IllegalArgumentException("Request or query is required");
        for (ConstraintViolation<T> violation : validator.validate(request, group)) {
            throw new IllegalArgumentException(violation.getPropertyPath() + ": " + violation.getMessage());
        }
    }

    /** Validates the resulting period, including a PATCH that changes only one endpoint. */
    private void validatePeriod(HoldingFinancialRecord existing, FinancialRecordRequest request) {
        LocalDate start = existing == null ? null : existing.getPeriodStart();
        LocalDate end = existing == null ? null : existing.getPeriodEnd();
        if (request.hasField("periodStart")) start = request.getPeriodStart();
        if (request.hasField("periodEnd")) end = request.getPeriodEnd();
        if ((start == null) != (end == null)) {
            throw new IllegalArgumentException("periodStart and periodEnd must be supplied or cleared together");
        }
        if (start != null && end.isBefore(start)) {
            throw new IllegalArgumentException("periodEnd must not precede periodStart");
        }
    }

    private void apply(HoldingFinancialRecord record, FinancialRecordRequest request) {
        if (request.hasField("category")) record.setCategory(request.getCategory());
        if (request.hasField("amount")) record.setAmount(request.getAmount());
        if (request.hasField("currency")) record.setCurrency(request.getCurrency());
        if (request.hasField("transactionDate")) record.setTransactionDate(request.getTransactionDate());
        if (request.hasField("periodStart")) record.setPeriodStart(request.getPeriodStart());
        if (request.hasField("periodEnd")) record.setPeriodEnd(request.getPeriodEnd());
        if (request.hasField("description")) {
            String description = request.getDescription();
            record.setDescription(description == null || description.isBlank() ? null : description.trim());
        }
    }

    private void requirePositive(Long id, String name) {
        if (id == null || id <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
