package com.geointelli.ai.property.service.service.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.service.CrmValidationService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

/** Trims optional text and bounds pagination before repository access. */
@Service
@RequiredArgsConstructor
public class CrmValidationServiceImpl implements CrmValidationService {
    private final Validator validator;

    @Override
    public void validate(Object request) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        for (var error : validator.validate(request)) {
            throw new IllegalArgumentException(error.getPropertyPath() + ": " + error.getMessage());
        }
    }

    @Override
    public String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public void requirePositive(Long id, String name) {
        if (id == null || id <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    @Override
    public Pageable page(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page must be nonnegative and size must be between 1 and 100; offset must fit an integer");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
}
