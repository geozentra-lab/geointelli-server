package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.PropertySearchRequest;
import com.geointelli.ai.property.service.dto.PropertySearchResponse;

public interface PropertySearchService {
    PropertySearchResponse search(PropertySearchRequest request);
}
