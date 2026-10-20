package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.PropertyExtraFeaturesDTO;

public interface ExtraFeatureService {
    int MAX_PAGE_SIZE = 200;
    PropertyExtraFeaturesDTO getExtraFeatures(Long propertyId, Integer year, boolean history, int page, int size);
}
