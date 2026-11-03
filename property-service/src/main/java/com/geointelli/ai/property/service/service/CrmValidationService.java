package com.geointelli.ai.property.service.service;

import org.springframework.data.domain.Pageable;

/** Common CRM input rules, also applied to direct service calls. */
public interface CrmValidationService {
    void validate(Object request);
    String normalize(String value);
    void requirePositive(Long id, String name);
    Pageable page(int page, int size);
}
