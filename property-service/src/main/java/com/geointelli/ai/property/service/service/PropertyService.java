package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.entity.Property;

public interface PropertyService {
    public Property saveProperty(Property property);
    public PropertyDTO getByFolioAPI(String folio);
    public PropertyDTO getByFolio(String folio);
    public List<String> getAllFolios();
    public List<Long> getAllIds();
}