package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.HoldingFinancingDTO;
import com.geointelli.ai.property.service.dto.HoldingOccupancyDTO;
import com.geointelli.ai.property.service.dto.PortfolioCreate;
import com.geointelli.ai.property.service.dto.PortfolioHoldingDTO;
import com.geointelli.ai.property.service.dto.PortfolioHoldingRequest;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;
import com.geointelli.ai.property.service.entity.HoldingFinancingType;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.PortfolioHolding;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.mapper.PortfolioMapper;
import com.geointelli.ai.property.service.repository.PortfolioHoldingRepository;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PortfolioHoldingService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

/** Records user-supplied holding facts and enforces parent ownership before every holding lookup. */
@Service
@Transactional
public class PortfolioHoldingServiceImpl implements PortfolioHoldingService {
    private final PortfolioRepository portfolios;
    private final PortfolioHoldingRepository holdings;
    private final PropertyRepository properties;
    private final CurrentUserService currentUser;
    private final PortfolioMapper mapper;
    private final Validator validator;

    public PortfolioHoldingServiceImpl(PortfolioRepository portfolios, PortfolioHoldingRepository holdings,
            PropertyRepository properties, CurrentUserService currentUser, PortfolioMapper mapper, Validator validator) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.properties = properties;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    public PortfolioHoldingDTO create(Long portfolioId, PortfolioHoldingRequest request) {
        Portfolio portfolio = owned(portfolioId, true);
        validate(request, PortfolioCreate.class);
        if (holdings.existsByPortfolio_IdAndProperty_Id(portfolioId, request.getPropertyId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Property is already in this portfolio");
        }
        Property property = properties.findById(request.getPropertyId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
        PortfolioHolding holding = new PortfolioHolding();
        holding.setPortfolio(portfolio);
        holding.setProperty(property);
        apply(holding, request);
        return mapper.toDTO(holdings.saveAndFlush(holding));
    }

    @Override
    public PortfolioPageDTO<PortfolioHoldingDTO> list(Long portfolioId, int page, int size) {
        owned(portfolioId, false);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
        Page<PortfolioHolding> result = holdings.findByPortfolio_Id(portfolioId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        List<PortfolioHoldingDTO> content = new ArrayList<>();
        for (PortfolioHolding holding : result) content.add(mapper.toDTO(holding));
        return new PortfolioPageDTO<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public PortfolioHoldingDTO get(Long portfolioId, Long holdingId) {
        owned(portfolioId, false);
        return mapper.toDTO(findHolding(portfolioId, holdingId));
    }

    @Override
    public PortfolioHoldingDTO update(Long portfolioId, Long holdingId, PortfolioHoldingRequest request) {
        owned(portfolioId, true);
        PortfolioHolding holding = findHolding(portfolioId, holdingId);
        validate(request, Default.class);
        if (request.hasField("propertyId") && !holding.getProperty().getId().equals(request.getPropertyId())) {
            throw new IllegalArgumentException("propertyId cannot be changed; remove the holding and add another property");
        }
        apply(holding, request);
        return mapper.toDTO(holdings.saveAndFlush(holding));
    }

    @Override
    public void delete(Long portfolioId, Long holdingId) {
        owned(portfolioId, true);
        holdings.delete(findHolding(portfolioId, holdingId));
    }

    private Portfolio owned(Long id, boolean lock) {
        requirePositive(id, "portfolioId");
        Long userId = currentUser.getCurrentUserEntity().getId();
        return (lock ? portfolios.findOwnedForUpdate(id, userId) : portfolios.findByIdAndUser_Id(id, userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
    }

    private PortfolioHolding findHolding(Long portfolioId, Long holdingId) {
        requirePositive(holdingId, "holdingId");
        return holdings.findByIdAndPortfolio_Id(holdingId, portfolioId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Holding not found in this portfolio"));
    }

    private void validate(PortfolioHoldingRequest request, Class<?> group) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        for (ConstraintViolation<PortfolioHoldingRequest> violation : validator.validate(request, group)) {
            throw new IllegalArgumentException(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        HoldingFinancingDTO financing = request.getFinancing();
        if (financing != null) {
            if ((financing.getOutstandingBalance() == null) != (financing.getBalanceAsOfDate() == null)) {
                throw new IllegalArgumentException("outstandingBalance and balanceAsOfDate must be supplied together");
            }
            if (financing.getType() == HoldingFinancingType.CASH
                    && (positive(financing.getLoanAmount()) || positive(financing.getOutstandingBalance())
                        || positive(financing.getMonthlyPayment()) || positive(financing.getInterestRatePercent())
                        || financing.getLoanTermMonths() != null)) {
                throw new IllegalArgumentException("CASH financing cannot include loan amounts, interest or a loan term");
            }
        }
        HoldingOccupancyDTO occupancy = request.getOccupancy();
        if (occupancy != null) {
            if (occupancy.getLeaseStartDate() != null && occupancy.getLeaseEndDate() != null
                    && occupancy.getLeaseEndDate().isBefore(occupancy.getLeaseStartDate())) {
                throw new IllegalArgumentException("leaseEndDate must not precede leaseStartDate");
            }
            if (occupancy.getStatus() != HoldingOccupancyStatus.RENTED
                    && (positive(occupancy.getMonthlyRent()) || occupancy.getLeaseStartDate() != null
                        || occupancy.getLeaseEndDate() != null)) {
                throw new IllegalArgumentException("Lease dates and nonzero monthly rent require RENTED occupancy");
            }
        }
    }

    private void apply(PortfolioHolding holding, PortfolioHoldingRequest request) {
        if (request.hasField("acquisitionDate")) holding.setAcquisitionDate(request.getAcquisitionDate());
        if (request.hasField("purchasePrice")) holding.setPurchasePrice(request.getPurchasePrice());
        if (request.hasField("acquisitionCosts")) holding.setAcquisitionCosts(request.getAcquisitionCosts());
        if (request.hasField("ownershipSharePercent")) holding.setOwnershipSharePercent(request.getOwnershipSharePercent());
        if (request.hasField("financing")) applyFinancing(holding, request.getFinancing());
        if (request.hasField("occupancy")) applyOccupancy(holding, request.getOccupancy());
    }

    private void applyFinancing(PortfolioHolding holding, HoldingFinancingDTO financing) {
        HoldingFinancingDTO value = financing == null ? new HoldingFinancingDTO() : financing;
        holding.setFinancingType(value.getType());
        holding.setLoanAmount(value.getLoanAmount());
        holding.setOutstandingBalance(value.getOutstandingBalance());
        holding.setBalanceAsOfDate(value.getBalanceAsOfDate());
        holding.setInterestRatePercent(value.getInterestRatePercent());
        holding.setLoanTermMonths(value.getLoanTermMonths());
        holding.setMonthlyPayment(value.getMonthlyPayment());
    }

    private void applyOccupancy(PortfolioHolding holding, HoldingOccupancyDTO occupancy) {
        HoldingOccupancyDTO value = occupancy == null ? new HoldingOccupancyDTO() : occupancy;
        holding.setOccupancyStatus(value.getStatus());
        holding.setMonthlyRent(value.getMonthlyRent());
        holding.setLeaseStartDate(value.getLeaseStartDate());
        holding.setLeaseEndDate(value.getLeaseEndDate());
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private void requirePositive(Long id, String name) {
        if (id == null || id <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
