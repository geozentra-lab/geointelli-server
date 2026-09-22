package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.entity.Property;

public interface PropertyService {
    Property saveProperty(Property property);
    PropertyDTO getByFolioAPI(String folio);
    PropertyDTO getByFolio(String folio);
    List<String> getAllFolios();
    List<Long> getAllIds();
    List<String> getAllFoliosForPropertyWithoutExtraFeatures();
}