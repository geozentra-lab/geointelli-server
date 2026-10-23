package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.PortfolioCreate;
import com.geointelli.ai.property.service.dto.PortfolioDTO;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;
import com.geointelli.ai.property.service.dto.PortfolioRequest;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.PortfolioMapper;
import com.geointelli.ai.property.service.repository.PortfolioHoldingCount;
import com.geointelli.ai.property.service.repository.PortfolioHoldingRepository;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PortfolioService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

/** Persists owned portfolios; transactions also allow the existing user service to provision a new user. */
@Service
@Transactional
public class PortfolioServiceImpl implements PortfolioService {
    private final PortfolioRepository portfolios;
    private final PortfolioHoldingRepository holdings;
    private final CurrentUserService currentUser;
    private final PortfolioMapper mapper;
    private final Validator validator;

    public PortfolioServiceImpl(PortfolioRepository portfolios, PortfolioHoldingRepository holdings,
            CurrentUserService currentUser, PortfolioMapper mapper, Validator validator) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    public PortfolioDTO create(PortfolioRequest request) {
        User user = currentUser.getCurrentUserEntity();
        validate(request, PortfolioCreate.class);
        Portfolio portfolio = new Portfolio();
        portfolio.setUser(user);
        portfolio.setName(request.getName().trim());
        portfolio.setDescription(normalize(request.getDescription()));
        return mapper.toDTO(portfolios.saveAndFlush(portfolio), 0);
    }

    @Override
    public PortfolioPageDTO<PortfolioDTO> list(int page, int size) {
        Long userId = currentUser.getCurrentUserEntity().getId();
        validatePage(page, size);
        Page<Portfolio> result = portfolios.findByUser_Id(userId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, Long> counts = new HashMap<>();
        if (!result.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (Portfolio portfolio : result) ids.add(portfolio.getId());
            for (PortfolioHoldingCount count : holdings.countForPortfolios(ids)) {
                counts.put(count.getPortfolioId(), count.getHoldingCount());
            }
        }
        List<PortfolioDTO> content = new ArrayList<>();
        for (Portfolio portfolio : result) {
            content.add(mapper.toDTO(portfolio, counts.getOrDefault(portfolio.getId(), 0L)));
        }
        return new PortfolioPageDTO<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public PortfolioDTO get(Long portfolioId) {
        Portfolio portfolio = owned(portfolioId, false);
        return mapper.toDTO(portfolio, holdings.countByPortfolio_Id(portfolioId));
    }

    @Override
    public PortfolioDTO update(Long portfolioId, PortfolioRequest request) {
        Portfolio portfolio = owned(portfolioId, true);
        validate(request, Default.class);
        if (request.isNameProvided()) portfolio.setName(request.getName().trim());
        if (request.isDescriptionProvided()) portfolio.setDescription(normalize(request.getDescription()));
        return mapper.toDTO(portfolios.saveAndFlush(portfolio), holdings.countByPortfolio_Id(portfolioId));
    }

    @Override
    public void delete(Long portfolioId) {
        Portfolio portfolio = owned(portfolioId, true);
        holdings.deleteForPortfolio(portfolioId);
        portfolios.delete(portfolio);
    }

    private Portfolio owned(Long id, boolean lock) {
        requirePositive(id, "portfolioId");
        Long userId = currentUser.getCurrentUserEntity().getId();
        return (lock ? portfolios.findOwnedForUpdate(id, userId) : portfolios.findByIdAndUser_Id(id, userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
    }

    private void validate(PortfolioRequest request, Class<?> group) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        for (ConstraintViolation<PortfolioRequest> violation : validator.validate(request, group)) {
            throw new IllegalArgumentException(violation.getPropertyPath() + ": " + violation.getMessage());
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requirePositive(Long id, String name) {
        if (id == null || id <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
    }
}
