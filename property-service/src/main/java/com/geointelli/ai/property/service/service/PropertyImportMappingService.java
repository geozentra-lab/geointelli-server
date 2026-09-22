package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImportMapping;

public interface PropertyImportMappingService {
    public Property getGeoZentraProperty(String county, String state, Long sourcePropertyId);
    public PropertyImportMapping saveMapping(String county, String state, Long sourcePropertyId, Property property);
}
